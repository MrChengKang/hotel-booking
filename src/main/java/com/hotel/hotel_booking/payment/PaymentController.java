package com.hotel.hotel_booking.payment;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PaymentController {
    private final PaymentService service;private final PaymentSettings settings;private final StripeWebhookVerifier verifier;private final RefundService refunds;
    public PaymentController(PaymentService service,PaymentSettings settings,StripeWebhookVerifier verifier,RefundService refunds){this.service=service;this.settings=settings;this.verifier=verifier;this.refunds=refunds;}
    public record Config(boolean enabled,String mode,int holdMinutes) {}
    @GetMapping("/api/payments/config") public Config config(){return new Config(settings.ready(),"test",35);}
    @PostMapping("/api/bookings/{id}/checkout") public PaymentService.Checkout checkout(Principal p,@PathVariable Long id){return service.checkout(p.getName(),id);}
    @GetMapping("/api/bookings/{id}/payment") public PaymentTransactions.View status(Principal p,@PathVariable Long id){return service.view(p.getName(),id);}
    @PostMapping("/api/payments/webhook") @ResponseStatus(HttpStatus.NO_CONTENT) public void webhook(HttpServletRequest request,@RequestHeader(value="Stripe-Signature",required=false) String signature)throws java.io.IOException{
        byte[] body=request.getInputStream().readNBytes(131073);if(body.length>131072)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Webhook too large.");var event=verifier.verify(body,signature);if(event!=null){if(event.type().startsWith("refund."))refunds.webhook(event.id(),event.sessionId());else service.webhook(event.id(),event.sessionId());}
    }
}
