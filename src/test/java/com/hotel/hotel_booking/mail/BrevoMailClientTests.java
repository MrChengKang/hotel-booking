package com.hotel.hotel_booking.mail;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class BrevoMailClientTests {
    HttpServer server;
    BrevoMailClient client;
    ObjectMapper json=new ObjectMapper();
    AtomicInteger calls=new AtomicInteger();
    String receivedKey,receivedBody,receivedPath;
    String response="{\"messageId\":\"<test-only@brevo.example>\"}";
    int status=201;
    long delay;
    static MockEnvironment configured() {
        return new MockEnvironment().withProperty("app.mail.brevo.api-key","test-only-api-secret")
                .withProperty("app.mail.brevo.sender-email","sender@example.com")
                .withProperty("app.mail.brevo.sender-name","STAY");
    }
    BrevoMailClient create(Duration timeout) {
        return new BrevoMailClient(new BrevoMailSettings(configured()),json,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v3/smtp/email"),timeout);
    }
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            calls.incrementAndGet();receivedKey=exchange.getRequestHeaders().getFirst("api-key");
            receivedPath=exchange.getRequestURI().toString();
            receivedBody=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            try {if(delay>0)Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            if(status==302)exchange.getResponseHeaders().set("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/must-not-follow");
            byte[] body=response.getBytes(StandardCharsets.UTF_8);
            try {exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);}
            finally {exchange.close();}
        });
        server.start();client=create(Duration.ofSeconds(2));
    }
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    @Test void sendsSingleRecipientTextUsingServerOnlyApiKey() {
        assertTrue(client.available());
        String link="https://stay.example.com/reset-password#token=test-private-token";
        client.sendText("guest@example.com","Reset your STAY password","Your link:\n"+link);
        assertEquals(1,calls.get());assertEquals("test-only-api-secret",receivedKey);
        assertEquals("/v3/smtp/email",receivedPath);
        var body=json.readTree(receivedBody);
        assertEquals("sender@example.com",body.path("sender").path("email").asText());
        assertEquals("STAY",body.path("sender").path("name").asText());
        assertEquals(1,body.path("to").size());
        assertEquals("guest@example.com",body.path("to").get(0).path("email").asText());
        assertEquals("Your link:\n"+link,body.path("textContent").asText());
        assertFalse(body.has("htmlContent"));assertFalse(body.has("templateId"));
        assertFalse(receivedBody.contains("test-only-api-secret"));
    }
    @Test void providerErrorsContainOnlyStatusAndAreNotRetried() {
        for(int code:new int[]{400,401,403,429,500}) {
            status=code;response="{\"message\":\"test-only-api-secret guest@example.com #token=private\"}";
            var failure=assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","private"));
            assertEquals(code,failure.httpStatus());assertNull(failure.getCause());
            assertFalse(failure.toString().contains("test-only-api-secret"));assertFalse(failure.toString().contains("guest@example.com"));
        }
        assertEquals(5,calls.get());
    }
    @Test void redirectsNeverForwardTheApiKey() {
        status=302;
        assertEquals(302,assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","text")).httpStatus());
        assertEquals(1,calls.get());
    }
    @Test void rejectsMalformedSuccessAndBoundsProviderResponses() {
        response="{}";assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","text"));
        response="not-json";assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","text"));
        response="x".repeat(65537);assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","text"));
    }
    @Test void totalDeadlineDoesNotRetryAnAmbiguousDelivery() {
        delay=500;client=create(Duration.ofMillis(150));
        assertThrows(MailDeliveryException.class,()->client.sendText("guest@example.com","Reset","text"));
        assertTrue(calls.get()<=1);
    }
    @Test void missingConfigAndInvalidRecipientsDoNotCallProvider() {
        assertFalse(new BrevoMailSettings(new MockEnvironment()).available());
        var unavailable=new BrevoMailClient(new BrevoMailSettings(new MockEnvironment()),json);
        assertThrows(IllegalStateException.class,()->unavailable.sendText("guest@example.com","Reset","text"));
        for(String email:new String[]{"not-an-address","Name <guest@example.com>","guest@example.com\r\nBcc: other@example.com"})
            assertThrows(IllegalArgumentException.class,()->client.sendText(email,"Reset","text"));
        assertEquals(0,calls.get());
    }
    @Test void unsafeSenderOrHeaderConfigIsUnavailable() {
        assertFalse(new BrevoMailSettings(configured().withProperty("app.mail.brevo.api-key","bad\r\nkey")).available());
        assertFalse(new BrevoMailSettings(configured().withProperty("app.mail.brevo.api-key","non-ascii-秘密")).available());
        assertFalse(new BrevoMailSettings(configured().withProperty("app.mail.brevo.api-key","internal space")).available());
        assertFalse(new BrevoMailSettings(configured().withProperty("app.mail.brevo.sender-email","Name <sender@example.com>")).available());
        assertFalse(new BrevoMailSettings(configured().withProperty("app.mail.brevo.sender-name","STAY\nother")).available());
    }
}
