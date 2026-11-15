package com.hotel.hotel_booking.payment;

import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StripeRefundGatewayTests {
    String intent(boolean live){return "{\"object\":\"payment_intent\",\"id\":\"pi_contract\",\"status\":\"succeeded\",\"livemode\":"+live+",\"amount_received\":20000,\"currency\":\"myr\"}";}
    String refund(){return "{\"object\":\"refund\",\"id\":\"re_contract\",\"payment_intent\":\"pi_contract\",\"amount\":20000,\"currency\":\"myr\",\"status\":\"pending\",\"metadata\":{\"app\":\"STAY\",\"booking_id\":\"7\",\"refund_key\":\"contract-key\"}}";}
    @SuppressWarnings("unchecked") HttpResponse<InputStream> response(String json){var r=(HttpResponse<InputStream>)mock(HttpResponse.class);when(r.statusCode()).thenReturn(200);when(r.body()).thenAnswer(c->new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));return r;}
    StripeRefundGateway gateway(HttpClient client){return new StripeRefundGateway(new PaymentSettings(true,"sk_test_fixture","whsec_fixture","http://localhost:3000"),new ObjectMapper(),client);}
    @SuppressWarnings("unchecked") @Test void testIntentIsVerifiedAndRefundUsesServerAmountAndDurableIdempotencyKey()throws Exception{
        var client=mock(HttpClient.class);var intentResponse=response(intent(false));var refundResponse=response(refund());
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(intentResponse,refundResponse,refundResponse);
        var gateway=gateway(client);var result=gateway.create(new RefundGateway.Request(7L,"contract-key","pi_contract",20000,"MYR"));assertEquals("pending",result.status());gateway.retrieve(result.id());
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(client,times(3)).send(capture.capture(),any(HttpResponse.BodyHandler.class));
        var requests=capture.getAllValues();assertEquals("/v1/payment_intents/pi_contract",requests.get(0).uri().getPath());
        var create=requests.get(1);assertEquals("/v1/refunds",create.uri().getPath());assertEquals("stay-refund-contract-key",create.headers().firstValue("Idempotency-Key").orElseThrow());
        var fields=StripeHttpGatewayTests.form(StripeHttpGatewayTests.body(create));assertEquals("20000",fields.get("amount"));assertEquals("pi_contract",fields.get("payment_intent"));assertEquals("7",fields.get("metadata[booking_id]"));assertEquals("contract-key",fields.get("metadata[refund_key]"));
        for(var request:requests){assertEquals("2025-06-30.basil",request.headers().firstValue("Stripe-Version").orElseThrow());assertEquals("https://api.stripe.com",request.uri().getScheme()+"://"+request.uri().getHost());}
    }
    @SuppressWarnings("unchecked") @Test void liveIntentIsRejectedBeforeAnyRefundPost()throws Exception{
        var client=mock(HttpClient.class);var intentResponse=response(intent(true));when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(intentResponse);
        assertThrows(ResponseStatusException.class,()->gateway(client).create(new RefundGateway.Request(7L,"key","pi_contract",20000,"MYR")));
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(capture.capture(),any(HttpResponse.BodyHandler.class));assertEquals("GET",capture.getValue().method());
    }
    @SuppressWarnings("unchecked") @Test void returnedRefundMustMatchTheRequestedProviderId()throws Exception{
        var client=mock(HttpClient.class);var refundResponse=response(refund());when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(refundResponse);
        assertThrows(ResponseStatusException.class,()->gateway(client).retrieve("re_other"));
    }
}
