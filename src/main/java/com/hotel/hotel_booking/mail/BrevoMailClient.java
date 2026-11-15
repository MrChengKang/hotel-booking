package com.hotel.hotel_booking.mail;

import com.hotel.hotel_booking.http.BoundedResponseBody;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

/** Server-to-server HTTPS only. There is deliberately no public send-email endpoint. */
public class BrevoMailClient {
    private final BrevoMailSettings settings;
    private final ObjectMapper json;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;

    public BrevoMailClient(BrevoMailSettings settings, ObjectMapper json) {
        this(settings, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.brevo.com/v3/smtp/email"), Duration.ofSeconds(15));
    }

    // Test endpoint and deadline injection are not exposed as runtime environment settings.
    BrevoMailClient(BrevoMailSettings settings, ObjectMapper json, HttpClient client, URI endpoint, Duration timeout) {
        this.settings=settings; this.json=json; this.client=client; this.endpoint=endpoint; this.timeout=timeout;
    }

    public boolean available() { return settings.available(); }

    public void sendText(String recipient, String subject, String text) {
        if (!available()) throw new IllegalStateException("Brevo email is not configured.");
        if (!BrevoMailSettings.validEmail(recipient)) throw new IllegalArgumentException("Invalid mail recipient.");
        final String body;
        try {
            body=json.writeValueAsString(Map.of("sender",Map.of("email",settings.senderEmail,"name",settings.senderName),
                    "to",List.of(Map.of("email",recipient)),"subject",subject,"textContent",text));
        } catch (RuntimeException failure) { throw new MailDeliveryException(0); }
        var request=HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("accept","application/json").header("content-type","application/json")
                .header("api-key",settings.apiKey).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var pending=client.sendAsync(request, info -> new BoundedResponseBody(65536));
        try {
            var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if (response.statusCode()!=201) throw new MailDeliveryException(response.statusCode());
            var node=json.readTree(response.body());
            String id=node.path("messageId").asText("");
            if (id.isBlank() || id.length()>1024) throw new MailDeliveryException(201);
        } catch (MailDeliveryException failure) { throw failure; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); pending.cancel(true); throw new MailDeliveryException(0);
        } catch (Exception failure) { pending.cancel(true); throw new MailDeliveryException(0); }
        // No automatic retries or SMTP fallback: a timed-out request may already have delivered.
    }
}
