package com.hotel.hotel_booking.payment;
import com.hotel.hotel_booking.booking.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import static com.hotel.hotel_booking.hotel.HotelManagementService.conflict;

@Service
public class PaymentService {
    private final PaymentTransactions work;private final StripeGateway stripe;private final PaymentSettings settings;
    public PaymentService(PaymentTransactions work,StripeGateway stripe,PaymentSettings settings){this.work=work;this.stripe=stripe;this.settings=settings;}
    public record Checkout(String url) {}
    private StripeGateway.Session session(PaymentTransactions.Plan plan){var s=plan.sessionId()==null?stripe.create(plan.request()):stripe.retrieve(plan.sessionId());if(!plan.request().attemptKey().equals(s.attemptKey())||!plan.bookingId().toString().equals(s.reference())||(plan.sessionId()!=null&&!plan.sessionId().equals(s.id())))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Payment response needs reconciliation.");return s;}
    public Checkout checkout(String email,Long id){settings.requireReady();var plan=work.prepare(email,id);if(plan==null)throw conflict("This unpaid reservation has expired. Search for available rooms again.");var s=session(plan);work.apply(s,null);if(!"open".equals(s.status()))throw conflict("Checkout is no longer open. Refresh the reservation status.");String url=work.checkoutUrl(email,id);if(url==null)throw conflict("Checkout is being cancelled. Refresh the reservation status.");return new Checkout(url);}
    public Optional<BookingDtos.Detail> cancelPending(String email,Long id){
        var before=work.view(email,id);if(before.bookingStatus()!=BookingStatus.PENDING_PAYMENT)return Optional.empty();
        if(work.releaseWithoutAttempt(id,email,true)){var result=work.detail(email,id);if(result.booking().status()==BookingStatus.CONFIRMED)throw conflict("Payment completed before cancellation. A refund workflow is required.");return Optional.of(result);}
        work.requestCancellation(email,id);reconcile(id,true);var result=work.detail(email,id);if(result.booking().status()==BookingStatus.PENDING_PAYMENT)throw conflict("Cancellation is queued while Stripe checkout is reconciled. The room hold is retained until checkout is closed.");if(result.booking().paymentStatus().equals("PAID"))throw conflict("Payment completed before cancellation. A refund workflow is required.");return Optional.of(result);
    }
    public void reconcile(Long id,boolean forceClose){
        var plan=work.plan(id);if(plan==null)return;
        if(plan.request()==null){if(forceClose||!Instant.now().isBefore(plan.expiresAt()))work.releaseWithoutAttempt(id,null,forceClose);return;}
        var s=session(plan);work.apply(s,null);
        if("open".equals(s.status())&&(forceClose||plan.cancellationRequested()||!Instant.now().isBefore(plan.expiresAt()))){s=stripe.expire(s.id());work.apply(s,null);}
    }
    public void webhook(String eventId,String sessionId){var s=stripe.retrieve(sessionId);if("STAY".equals(s.app()))work.apply(s,eventId);}
    public PaymentTransactions.View view(String email,Long id){return work.view(email,id);}
}
