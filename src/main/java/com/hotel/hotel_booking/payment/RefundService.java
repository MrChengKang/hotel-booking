package com.hotel.hotel_booking.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RefundService {
    private final RefundTransactions work;private final RefundGateway stripe;private final PaymentSettings settings;
    public RefundService(RefundTransactions work,RefundGateway stripe,PaymentSettings settings){this.work=work;this.stripe=stripe;this.settings=settings;}
    public RefundTransactions.View view(String email,Long id){return work.view(email,id);}
    public RefundTransactions.View request(String email,Long id,BigDecimal expectedAmount){
        settings.requireReady();var plan=work.prepare(email,id,expectedAmount);reconcile(id,plan);return work.view(email,id);
    }
    public void reconcile(Long id){var plan=work.plan(id);if(plan!=null)reconcile(id,plan);}
    private void reconcile(Long id,RefundTransactions.Plan plan){
        if(Set.of("SUCCEEDED","FAILED","REVIEW_REQUIRED").contains(plan.status()))return;
        if(plan.refundId()==null&&!Instant.now().isBefore(plan.createdAt().plusSeconds(23*3600))){work.requireReview(id);return;}
        var response=plan.refundId()==null?stripe.create(plan.request()):stripe.retrieve(plan.refundId());
        if(!plan.request().key().equals(response.key())||!plan.request().bookingId().toString().equals(response.bookingId())
            ||!plan.request().paymentIntent().equals(response.paymentIntent())||(plan.refundId()!=null&&!plan.refundId().equals(response.id())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Stripe refund does not match this reservation. Reconciliation is required.");
        work.apply(response,null);
    }
    public void webhook(String eventId,String refundId){var r=stripe.retrieve(refundId);if("STAY".equals(r.app()))work.apply(r,eventId);}
}
