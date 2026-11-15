package com.hotel.hotel_booking.payment;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import tools.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class StripeHttpGateway implements StripeGateway {
    private final PaymentSettings settings;private final ObjectMapper json;
    private final HttpClient client;
    @Autowired public StripeHttpGateway(PaymentSettings settings,ObjectMapper json){this(settings,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());}
    StripeHttpGateway(PaymentSettings settings,ObjectMapper json,HttpClient client){this.settings=settings;this.json=json;this.client=client;}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Unable to verify Stripe checkout. Your reservation is retained for reconciliation; retry shortly.");}
    private Session call(String method,String path,Map<String,String> fields,String key){
        settings.requireReady();
        var request=HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/checkout/sessions"+path)).timeout(Duration.ofSeconds(15))
            .header("Authorization","Bearer "+settings.secret).header("Stripe-Version","2025-06-30.basil");
        if(key!=null)request.header("Idempotency-Key",key);
        if(method.equals("POST")){String form=fields.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));request.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));}else request.GET();
        try{
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;try(var stream=response.body()){bytes=stream.readNBytes(262145);}if(bytes.length>262144||response.statusCode()<200||response.statusCode()>=300)throw unavailable();
            var n=json.readTree(bytes);if(!"checkout.session".equals(n.path("object").asText())||!n.has("livemode")||!n.has("amount_total")||!n.has("expires_at"))throw unavailable();
            return new Session(n.path("id").asText(),n.path("url").asText(null),n.path("status").asText(),n.path("payment_status").asText(),n.path("amount_total").asLong(),n.path("currency").asText(),n.path("client_reference_id").asText(),n.path("metadata").path("attempt_key").asText(),n.path("payment_intent").asText(null),Instant.ofEpochSecond(n.path("expires_at").asLong()),n.path("livemode").asBoolean(),n.path("metadata").path("app").asText());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(ResponseStatusException e){throw e;}catch(Exception e){throw unavailable();}
    }
    private String path(String id){if(id==null||!id.matches("cs_test_[A-Za-z0-9_]+"))throw unavailable();return "/"+id;}
    @Override public Session create(Request r){
        var fields=new LinkedHashMap<String,String>();fields.put("mode","payment");fields.put("payment_method_types[0]","card");fields.put("client_reference_id",r.bookingId().toString());fields.put("metadata[app]","STAY");fields.put("metadata[booking_id]",r.bookingId().toString());fields.put("metadata[attempt_key]",r.attemptKey());
        fields.put("line_items[0][quantity]","1");fields.put("line_items[0][price_data][currency]",r.currency().toLowerCase(Locale.ROOT));fields.put("line_items[0][price_data][unit_amount]",Long.toString(r.amount()));fields.put("line_items[0][price_data][product_data][name]",r.name());fields.put("expires_at",Long.toString(r.expiresAt().getEpochSecond()));
        fields.put("success_url",settings.frontend+"/bookings?booking="+r.bookingId()+"&payment=return");fields.put("cancel_url",settings.frontend+"/bookings?booking="+r.bookingId()+"&payment=cancelled");
        return call("POST","",fields,"stay-checkout-"+r.attemptKey());
    }
    @Override public Session retrieve(String id){return call("GET",path(id),Map.of(),null);}
    @Override public Session expire(String id){try{return call("POST",path(id)+"/expire",Map.of(),null);}catch(ResponseStatusException e){return retrieve(id);}}
}
