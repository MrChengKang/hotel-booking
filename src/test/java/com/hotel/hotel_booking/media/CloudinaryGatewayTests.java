package com.hotel.hotel_booking.media;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class CloudinaryGatewayTests {
    HttpServer server;
    CloudinaryGateway gateway;
    final String key="cld-12345678-1234-1234-1234-123456789abc.png";
    Map<String,String> sent;
    String action;
    int status=200;
    byte[] response;
    AtomicInteger calls=new AtomicInteger();

    static MockEnvironment environment() {
        return new MockEnvironment().withProperty("app.media.provider","cloudinary")
                .withProperty("app.media.cloudinary.cloud-name","test-cloud")
                .withProperty("app.media.cloudinary.api-key","test-key")
                .withProperty("app.media.cloudinary.api-secret","test-secret");
    }
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); action=exchange.getRequestURI().getPath();
            String form=exchange.getRequestMethod().equals("GET")?exchange.getRequestURI().getRawQuery():new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            sent=new TreeMap<>();
            for(String item:form.split("&")) {var pair=item.split("=",2);sent.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
            if(status==302)exchange.getResponseHeaders().add("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/must-not-follow");
            exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        gateway=new CloudinaryGateway(new CloudinarySettings(environment()),new ObjectMapper(),HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Clock.fixed(Instant.ofEpochSecond(1700000000),ZoneOffset.UTC));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    void reply(String body){response=body.getBytes(StandardCharsets.UTF_8);}
    void signatureIsValid() throws Exception {
        var fields=new TreeMap<>(sent);fields.remove("file");fields.remove("api_key");fields.remove("signature");
        String canonical=fields.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("&"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((canonical+"test-secret").getBytes(StandardCharsets.UTF_8))),sent.get("signature"));
        assertEquals("test-key",sent.get("api_key"));assertFalse(sent.containsValue("test-secret"));
    }
    @Test void uploadsSignedBytesWithPrivateDeliveryAndNoOverwrite() throws Exception {
        reply("{\"public_id\":\"stay/12345678-1234-1234-1234-123456789abc\",\"resource_type\":\"image\",\"type\":\"authenticated\"}");
        gateway.upload(key,new byte[]{1,2,3});
        assertEquals("/v1_1/test-cloud/image/upload",action);signatureIsValid();
        assertEquals("authenticated",sent.get("type"));assertEquals("false",sent.get("overwrite"));
        assertEquals("data:image/png;base64,AQID",sent.get("file"));
    }
    @Test void downloadsWithAnExpiringServerOnlySignature() throws Exception {
        response=new byte[]{1,2,3};assertArrayEquals(response,gateway.read(key));signatureIsValid();
        assertEquals("/v1_1/test-cloud/image/download",action);assertEquals("1700000060",sent.get("expires_at"));
        assertEquals("png",sent.get("format"));assertEquals("authenticated",sent.get("type"));
    }
    @Test void deletionIsSignedAndIdempotent() throws Exception {
        reply("{\"result\":\"not found\"}");assertDoesNotThrow(()->gateway.delete(key));signatureIsValid();
        assertEquals("/v1_1/test-cloud/image/destroy",action);assertEquals("true",sent.get("invalidate"));
    }
    @Test void upstreamFailuresAndRedirectsAreSafeAndNeverExposeDetails() {
        for(int code:new int[]{401,429,500,302}) {
            status=code;reply("test-secret private-provider-error");
            var failure=assertThrows(ResponseStatusException.class,()->gateway.read(key));
            assertEquals(503,failure.getStatusCode().value());assertFalse(failure.toString().contains("test-secret"));
        }
        assertEquals(4,calls.get());
    }
    @Test void missingAndOversizedImagesAreBounded() {
        status=404;reply("missing");assertEquals(404,assertThrows(ResponseStatusException.class,()->gateway.read(key)).getStatusCode().value());
        status=200;response=new byte[CloudinaryGateway.MAX_IMAGE_BYTES+1];
        assertEquals(503,assertThrows(ResponseStatusException.class,()->gateway.read(key)).getStatusCode().value());
    }
    @Test void rejectsInvalidKeysAndUnexpectedUploadResponses() {
        reply("{}");assertThrows(IllegalArgumentException.class,()->gateway.read("../../anything.png"));assertEquals(0,calls.get());
        assertThrows(ResponseStatusException.class,()->gateway.upload(key,new byte[]{1}));
    }
    @Test void cloudModeRequiresCredentialsAndKnownProvider() {
        assertThrows(IllegalStateException.class,()->new CloudinarySettings(new MockEnvironment().withProperty("app.media.provider","cloudinary")));
        assertThrows(IllegalStateException.class,()->new CloudinarySettings(new MockEnvironment().withProperty("app.media.provider","typo")));
        assertDoesNotThrow(()->new CloudinarySettings(new MockEnvironment()));
    }
    @Test void validLargeImageReadsPreserveAllBytes() {
        response=new byte[4*1024*1024];new java.util.Random(7).nextBytes(response);
        assertArrayEquals(response,gateway.read(key));
    }
}
