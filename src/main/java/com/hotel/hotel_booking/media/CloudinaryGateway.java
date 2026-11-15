package com.hotel.hotel_booking.media;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Server-side signed Upload API calls. Signed download URLs never leave this process. */
@Component
public class CloudinaryGateway {
    private final CloudinarySettings settings;
    private final ObjectMapper json;
    private final HttpClient client;
    private final URI base;
    private final Clock clock;
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    @Autowired
    public CloudinaryGateway(CloudinarySettings settings, ObjectMapper json) {
        this(settings, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.cloudinary.com"), Clock.systemUTC());
    }

    // Endpoint injection is package-private for isolated HTTP contract tests, not a runtime setting.
    CloudinaryGateway(CloudinarySettings settings, ObjectMapper json, HttpClient client, URI base, Clock clock) {
        this.settings = settings; this.json = json; this.client = client; this.base = base; this.clock = clock;
    }

    public static boolean cloudKey(String key) {
        return key != null && key.matches("cld-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)");
    }

    private String publicId(String key) {
        if (!cloudKey(key)) throw new IllegalArgumentException("Invalid cloud image key.");
        return settings.prefix + "/" + key.substring(4, key.lastIndexOf('.'));
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is unavailable. Please try again.");
    }

    private TreeMap<String,String> fields() {
        settings.requireReady();
        var fields = new TreeMap<String,String>();
        fields.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
        return fields;
    }

    private Map<String,String> signed(TreeMap<String,String> fields) {
        String serialized = fields.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).collect(Collectors.joining("&"));
        try {
            var result = new TreeMap<>(fields);
            result.put("signature", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((serialized+settings.apiSecret).getBytes(StandardCharsets.UTF_8))));
            result.put("api_key", settings.apiKey);
            return result;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String form(Map<String,String> fields) {
        return fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)+"="
                +URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private URI endpoint(String action) { return base.resolve("/v1_1/"+settings.cloudName+"/image/"+action); }

    private byte[] request(HttpRequest request, int max, boolean missingAllowed) {
        var pending = client.sendAsync(request, info -> new LimitedBody(max));
        try {
            var response = pending.get(60, TimeUnit.SECONDS);
            if (missingAllowed && response.statusCode() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            return response.body();
        } catch (ResponseStatusException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); pending.cancel(true); throw unavailable(); }
        catch (Exception e) { pending.cancel(true); throw unavailable(); }
    }

    private byte[] post(String action, Map<String,String> fields) {
        return request(HttpRequest.newBuilder(endpoint(action)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(), 65536, false);
    }

    public void upload(String key, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw unavailable();
        var fields = fields();
        fields.put("public_id", publicId(key)); fields.put("type", "authenticated");
        fields.put("overwrite", "false");
        var params = new TreeMap<>(signed(fields));
        params.put("file", "data:"+(key.endsWith(".jpg") ? "image/jpeg" : "image/png")+";base64,"+Base64.getEncoder().encodeToString(bytes));
        try {
            var node = json.readTree(post("upload", params));
            if (!publicId(key).equals(node.path("public_id").asText())
                    || !"image".equals(node.path("resource_type").asText())
                    || !"authenticated".equals(node.path("type").asText())) throw unavailable();
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw unavailable(); }
    }

    public byte[] read(String key) {
        var fields = fields();
        fields.put("public_id", publicId(key)); fields.put("format", key.substring(key.lastIndexOf('.')+1));
        fields.put("type", "authenticated"); fields.put("attachment", "false");
        fields.put("expires_at", Long.toString(clock.instant().plusSeconds(60).getEpochSecond()));
        URI url = URI.create(endpoint("download")+"?"+form(signed(fields)));
        return request(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(60)).GET().build(), MAX_IMAGE_BYTES, true);
    }

    public void delete(String key) {
        var fields = fields(); fields.put("public_id", publicId(key));
        fields.put("type", "authenticated"); fields.put("invalidate", "true");
        try {
            var result = json.readTree(post("destroy", signed(fields))).path("result").asText();
            if (!Set.of("ok", "not found").contains(result)) throw unavailable();
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw unavailable(); }
    }

    /** Bounds response memory and allows an overall timeout including the response body. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> done = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return done; }
        @Override public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > max-bytes.size()) {
                    subscription.cancel(); done.completeExceptionally(new IllegalStateException("Response exceeded photo storage limit.")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { done.completeExceptionally(error); }
        @Override public void onComplete() { done.complete(bytes.toByteArray()); }
    }
}
