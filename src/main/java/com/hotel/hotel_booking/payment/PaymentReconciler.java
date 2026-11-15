package com.hotel.hotel_booking.payment;
import com.hotel.hotel_booking.booking.BookingRepository;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;

@Component @EnableScheduling
@ConditionalOnProperty(name="app.payments.scheduling-enabled",havingValue="true",matchIfMissing=true)
public class PaymentReconciler {
    private final BookingRepository bookings;private final PaymentService payments;private final PaymentSettings settings;private long cursor=0;
    private final RefundAttemptRepository attempts;private final RefundService refunds;private long refundCursor=0;
    public PaymentReconciler(BookingRepository bookings,PaymentService payments,PaymentSettings settings,RefundAttemptRepository attempts,RefundService refunds){this.bookings=bookings;this.payments=payments;this.settings=settings;this.attempts=attempts;this.refunds=refunds;}
    @Scheduled(fixedDelayString="${app.payments.reconcile-delay-ms:60000}",initialDelayString="${app.payments.reconcile-delay-ms:60000}")
    public void run(){if(!settings.ready())return;var ids=bookings.pendingIdsAfter(cursor,PageRequest.of(0,25));if(ids.isEmpty()){cursor=0;return;}for(Long id:ids){cursor=id;try{payments.reconcile(id,false);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payment reconciliation deferred for booking {}.",id);}}}
    @Scheduled(fixedDelayString="${app.payments.reconcile-delay-ms:60000}",initialDelayString="${app.payments.reconcile-delay-ms:60000}")
    public void refunds(){if(!settings.ready())return;var ids=attempts.pendingIdsAfter(refundCursor,PageRequest.of(0,25));if(ids.isEmpty()){refundCursor=0;return;}for(Long id:ids){refundCursor=id;try{refunds.reconcile(id);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Refund reconciliation deferred for booking {}.",id);}}}
}
