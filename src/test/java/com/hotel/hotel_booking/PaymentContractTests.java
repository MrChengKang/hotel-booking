package com.hotel.hotel_booking;
import com.hotel.hotel_booking.payment.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PaymentContractTests {
    @Test void liveKeysAndUnconfiguredPaymentsFailClosed(){
        assertThrows(IllegalArgumentException.class,()->new PaymentSettings(true,"sk_live_forbidden","whsec_fixture","http://localhost:3000"));
        var empty=new PaymentSettings(true,"","","http://localhost:3000");assertFalse(empty.ready());assertThrows(ResponseStatusException.class,empty::requireReady);
        assertThrows(IllegalArgumentException.class,()->new PaymentSettings(true,"sk_test_fixture","whsec_fixture","http://untrusted.invalid"));
    }
    @Test void moneyUsesExactCurrencyMinorUnits(){
        assertEquals(125,PaymentSettings.minor(new BigDecimal("1.25"),"MYR"));assertEquals(125,PaymentSettings.minor(new BigDecimal("125"),"JPY"));
        assertThrows(ResponseStatusException.class,()->PaymentSettings.minor(new BigDecimal("1.5"),"JPY"));assertThrows(ResponseStatusException.class,()->PaymentSettings.minor(new BigDecimal("1"),"CHF"));assertThrows(ResponseStatusException.class,()->PaymentSettings.minor(new BigDecimal("1000000"),"USD"));
    }
    @Test void signatureRotationAcceptsAValidV1AndRejectsLiveEvents()throws Exception{
        var verifier=new StripeWebhookVerifier(new PaymentSettings(true,"sk_test_fixture","whsec_fixture","http://localhost:3000"),new ObjectMapper());long now=Instant.now().getEpochSecond();
        String body="{\"id\":\"evt_fixture\",\"livemode\":false,\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_fixture\",\"object\":\"checkout.session\"}}}";
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String signature=HexFormat.of().formatHex(mac.doFinal((now+"."+body).getBytes(StandardCharsets.UTF_8)));
        assertEquals("cs_test_fixture",verifier.verify(body.getBytes(StandardCharsets.UTF_8),"t="+now+",v1="+"0".repeat(64)+",v1="+signature).sessionId());
        String live=body.replace("false","true");String liveSignature=HexFormat.of().formatHex(mac.doFinal((now+"."+live).getBytes(StandardCharsets.UTF_8)));assertThrows(ResponseStatusException.class,()->verifier.verify(live.getBytes(StandardCharsets.UTF_8),"t="+now+",v1="+liveSignature));
    }
}
