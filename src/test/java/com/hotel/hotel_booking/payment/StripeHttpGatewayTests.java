package com.hotel.hotel_booking.payment;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripeHttpGatewayTests {
    @SuppressWarnings("unchecked")
    @Test void hostedCardCheckoutPinsTheVerifiedApiAndServerOwnedAmounts()throws Exception {
        var client=mock(HttpClient.class);HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String json="{\"object\":\"checkout.session\",\"id\":\"cs_test_contract\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_contract\",\"status\":\"open\",\"payment_status\":\"unpaid\",\"amount_total\":20000,\"currency\":\"myr\",\"client_reference_id\":\"7\",\"metadata\":{\"app\":\"STAY\",\"attempt_key\":\"contract-key\"},\"payment_intent\":null,\"expires_at\":1900000000,\"livemode\":false}";
        when(response.body()).thenAnswer(call->new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var gateway=new StripeHttpGateway(new PaymentSettings(true,"sk_test_fixture","whsec_fixture","http://localhost:3000"),new ObjectMapper(),client);
        var result=gateway.create(new StripeGateway.Request(7L,"contract-key",20000,"MYR","City Hotel",Instant.ofEpochSecond(1900000000)));
        assertEquals("cs_test_contract",result.id());assertEquals(20000,result.amount());gateway.retrieve(result.id());gateway.expire(result.id());
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(client,times(3)).send(capture.capture(),any(HttpResponse.BodyHandler.class));
        for(var request:capture.getAllValues()){assertEquals("2025-06-30.basil",request.headers().firstValue("Stripe-Version").orElseThrow());assertEquals("https",request.uri().getScheme());assertEquals("api.stripe.com",request.uri().getHost());}
        var create=capture.getAllValues().get(0);assertEquals("stay-checkout-contract-key",create.headers().firstValue("Idempotency-Key").orElseThrow());
        var fields=form(body(create));assertEquals("card",fields.get("payment_method_types[0]"));assertEquals("20000",fields.get("line_items[0][price_data][unit_amount]"));assertEquals("7",fields.get("metadata[booking_id]"));assertEquals("contract-key",fields.get("metadata[attempt_key]"));assertEquals("http://localhost:3000/bookings?booking=7&payment=return",fields.get("success_url"));
    }
    static Map<String,String> form(String body){var result=new HashMap<String,String>();for(String field:body.split("&")){String[] pair=field.split("=",2);result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}return result;}
    static String body(HttpRequest request)throws Exception {
        var bytes=new ByteArrayOutputStream();var finished=new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer buffer){byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
            public void onError(Throwable error){finished.completeExceptionally(error);}
            public void onComplete(){finished.complete(bytes.toString(StandardCharsets.UTF_8));}
        });return finished.get(5,TimeUnit.SECONDS);
    }
}
