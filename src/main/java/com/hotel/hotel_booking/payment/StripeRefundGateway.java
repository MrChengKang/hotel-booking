package com.hotel.hotel_booking.payment;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Component
public class StripeRefundGateway implements RefundGateway {
    private final PaymentSettings settings;
    private final ObjectMapper json;
    private final HttpClient client;

    @Autowired public StripeRefundGateway(PaymentSettings settings, ObjectMapper json) {
        this(settings, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    StripeRefundGateway(PaymentSettings settings, ObjectMapper json, HttpClient client) {
        this.settings = settings; this.json = json; this.client = client;
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Unable to verify the refund. Your booking and rooms are retained; the same refund request will be reconciled.");
    }
    private JsonNode call(String method, String path, Map<String,String> fields, String key) {
        settings.requireReady();
        var builder = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/" + path))
            .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + settings.secret)
            .header("Stripe-Version", "2025-06-30.basil");
        if (key != null) builder.header("Idempotency-Key", key);
        if (method.equals("POST")) {
            String body = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
            builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        } else builder.GET();
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(262145); }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length > 262144) throw unavailable();
            return json.readTree(bytes);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw unavailable(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private Refund refund(JsonNode node) {
        if (!"refund".equals(node.path("object").asText()) || !node.has("amount")) throw unavailable();
        return new Refund(node.path("id").asText(), node.path("payment_intent").asText(), node.path("amount").asLong(),
            node.path("currency").asText(), node.path("status").asText(), node.path("metadata").path("refund_key").asText(),
            node.path("metadata").path("booking_id").asText(), node.path("metadata").path("app").asText());
    }
    @Override public Refund create(Request request) {
        if (!request.paymentIntent().matches("pi_[A-Za-z0-9_]+")) throw unavailable();
        // Refund objects have no livemode field. Verify their original PaymentIntent
        // with the test-only credentials before making a provider-side mutation.
        var intent = call("GET", "payment_intents/" + request.paymentIntent(), Map.of(), null);
        if (!"payment_intent".equals(intent.path("object").asText()) || !intent.has("livemode")
            || intent.path("livemode").asBoolean() || !request.paymentIntent().equals(intent.path("id").asText())
            || !"succeeded".equals(intent.path("status").asText()) || !intent.has("amount_received")
            || intent.path("amount_received").asLong() != request.amount()
            || !request.currency().equalsIgnoreCase(intent.path("currency").asText())) throw unavailable();
        var fields = new LinkedHashMap<String,String>();
        fields.put("payment_intent", request.paymentIntent()); fields.put("amount", Long.toString(request.amount()));
        fields.put("reason", "requested_by_customer"); fields.put("metadata[app]", "STAY");
        fields.put("metadata[booking_id]", request.bookingId().toString()); fields.put("metadata[refund_key]", request.key());
        return refund(call("POST", "refunds", fields, "stay-refund-" + request.key()));
    }
    @Override public Refund retrieve(String id) {
        if (id == null || !id.matches("re_[A-Za-z0-9_]+")) throw unavailable();
        var result=refund(call("GET", "refunds/" + id, Map.of(), null));
        if(!id.equals(result.id()))throw unavailable();
        return result;
    }
}
