package com.hotel.hotel_booking.payment;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

@Component
public class StripeWebhookVerifier {
    private final PaymentSettings settings;private final ObjectMapper json;
    public StripeWebhookVerifier(PaymentSettings settings,ObjectMapper json){this.settings=settings;this.json=json;}
    public record Event(String id,String type,String sessionId) {}
    public Event verify(byte[] body,String signature){
        settings.requireReady();
        try{
            if(body.length>131072||signature==null||signature.length()>4096)throw new IllegalArgumentException();
            Long timestamp=null;var signatures=new ArrayList<String>();
            for(String part:signature.split(",")){String[] pair=part.strip().split("=",2);if(pair.length!=2)continue;if(pair[0].equals("t")){if(timestamp!=null)throw new IllegalArgumentException();timestamp=Long.parseLong(pair[1]);}if(pair[0].equals("v1")&&pair[1].matches("[0-9a-fA-F]{64}"))signatures.add(pair[1]);}
            long now=Instant.now().getEpochSecond();if(timestamp==null||timestamp<now-300||timestamp>now+300||signatures.isEmpty())throw new IllegalArgumentException();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));byte[] expected=mac.doFinal(body);boolean verified=false;for(String value:signatures)verified|=MessageDigest.isEqual(expected,HexFormat.of().parseHex(value));if(!verified)throw new IllegalArgumentException();
            var n=json.readTree(body);if(!n.path("id").asText().matches("evt_[A-Za-z0-9_]+")||!n.has("livemode")||n.path("livemode").asBoolean())throw new IllegalArgumentException();
            String type=n.path("type").asText();
            if(Set.of("refund.created","refund.updated","refund.failed").contains(type)){
                var refund=n.path("data").path("object");String id=refund.path("id").asText();
                if(!"refund".equals(refund.path("object").asText())||!id.matches("re_[A-Za-z0-9_]+"))throw new IllegalArgumentException();
                return new Event(n.path("id").asText(),type,id);
            }
            if(!Set.of("checkout.session.completed","checkout.session.expired","checkout.session.async_payment_succeeded","checkout.session.async_payment_failed").contains(type))return null;
            var object=n.path("data").path("object");String session=object.path("id").asText();if(!"checkout.session".equals(object.path("object").asText())||!session.matches("cs_test_[A-Za-z0-9_]+"))throw new IllegalArgumentException();return new Event(n.path("id").asText(),type,session);
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe webhook signature or event.");}
    }
}
