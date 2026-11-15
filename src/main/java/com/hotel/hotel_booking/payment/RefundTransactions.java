package com.hotel.hotel_booking.payment;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.notification.NotificationService;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class RefundTransactions {
    private final BookingRepository bookings;
    private final UserRepository users;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final RoomInventoryRepository inventory;
    private final PaymentAttemptRepository payments;
    private final RefundAttemptRepository refunds;
    private final PaymentEventRepository events;
    private final NotificationService notifications;
    public RefundTransactions(BookingRepository bookings,UserRepository users,HotelRepository hotels,
        RoomTypeRepository rooms,RoomInventoryRepository inventory,PaymentAttemptRepository payments,
        RefundAttemptRepository refunds,PaymentEventRepository events,NotificationService notifications) {
        this.bookings=bookings;this.users=users;this.hotels=hotels;this.rooms=rooms;this.inventory=inventory;
        this.payments=payments;this.refunds=refunds;this.events=events;this.notifications=notifications;
    }
    public record View(Long bookingId,boolean allowed,String reason,Instant deadline,BigDecimal refundAmount,
                       String currencyCode,String status,String policyVersion,String hotelTimeZone) {}
    public record Plan(RefundGateway.Request request,String refundId,String status,Instant createdAt) {}
    private Booking owned(String email,Long id) {
        var user=users.findByEmail(email).orElseThrow(HotelManagementService::missing);
        if(user.getRole()!=Role.USER)throw new SecurityException();
        return bookings.findByIdAndGuestId(id,user.getId()).orElseThrow(HotelManagementService::missing);
    }
    // Same global order as payment/booking writers; the refund lock follows payment.
    private Booking locked(Long id,String email) {
        var scope=bookings.scope(id).orElseThrow(HotelManagementService::missing);
        var user=users.findLockedById(scope.getGuestId()).orElseThrow(HotelManagementService::missing);
        if(email!=null&&!email.equals(user.getEmail()))throw missing();
        hotels.findLockedById(scope.getHotelId()).orElseThrow(HotelManagementService::missing);
        rooms.findLockedById(scope.getRoomId()).orElseThrow(HotelManagementService::missing);
        return bookings.findLockedById(id).orElseThrow(HotelManagementService::missing);
    }
    private String reason(Booking b,RefundAttempt refund,PaymentAttempt payment) {
        if(refund!=null) return switch(refund.getStatus()) {
            case "SUCCEEDED" -> "The booking was cancelled and its sandbox payment refunded.";
            case "FAILED" -> "The refund failed. Your booking and rooms are retained. Contact support.";
            case "REVIEW_REQUIRED" -> "The refund needs manual review. Your booking and rooms are retained.";
            default -> "Your refund is being processed. Rooms remain reserved until it succeeds.";
        };
        if(b.getStatus()!=BookingStatus.CONFIRMED||!b.getPaymentStatus().equals("PAID"))return "Only paid, confirmed reservations can request a refund.";
        if(!CancellationPolicy.beforeCheckIn(Instant.now(),b.getCheckIn(),b.getHotelTimeZone()))return "Online cancellation closes at midnight on the hotel's local check-in date.";
        if(payment==null||!"PAID".equals(payment.getStatus())||payment.getPaymentIntentId()==null)return "The original online payment could not be verified. Contact support.";
        return null;
    }
    public View view(String email,Long id) {
        var b=owned(email,id);var r=refunds.findByBookingId(id).orElse(null);var p=payments.findByBookingId(id).orElse(null);
        var reason=reason(b,r,p);
        return new View(id,reason==null,reason,CancellationPolicy.deadline(b.getCheckIn(),b.getHotelTimeZone()),
            b.getTotal(),b.getCurrencyCode(),r==null?"NOT_REQUESTED":r.getStatus(),"FULL_BEFORE_CHECKIN_V1",b.getHotelTimeZone());
    }
    private Plan plan(RefundAttempt r) {return new Plan(r.request(),r.getRefundId(),r.getStatus(),r.getCreatedAt());}
    public Plan plan(Long id) {return refunds.findByBookingId(id).map(this::plan).orElse(null);}
    @Transactional public Plan prepare(String email,Long id,BigDecimal expectedAmount) {
        var b=locked(id,email);
        var p=payments.findLockedByBookingId(id).orElse(null);
        var r=refunds.findLockedByBookingId(id).orElse(null);
        if(expectedAmount.compareTo(b.getTotal())!=0)throw conflict("The refund quote changed. Refresh the cancellation details.");
        if(r!=null)return plan(r); // retries reuse the original request, including after the deadline
        String reason=reason(b,null,p);if(reason!=null)throw conflict(reason);
        long amount=PaymentSettings.minor(b.getTotal(),b.getCurrencyCode());
        if(amount<=0||p.getAmountMinor()!=amount||!p.getCurrency().equals(b.getCurrencyCode()))throw conflict("The original online payment could not be verified. Contact support.");
        r=refunds.saveAndFlush(new RefundAttempt(b,p.getPaymentIntentId(),amount));
        b.refundPending();notifyBoth(b,"refund-requested:","Refund requested","A full sandbox refund was requested. The booking remains reserved until Stripe confirms success.");
        return plan(r);
    }
    @Transactional public void requireReview(Long id) {
        var b=locked(id,null);payments.findLockedByBookingId(id);
        var r=refunds.findLockedByBookingId(id).orElseThrow(HotelManagementService::missing);
        if(!r.getStatus().equals("INITIALIZING")||r.getRefundId()!=null)return;
        r.update(null,"REVIEW_REQUIRED");
        notifyBoth(b,"refund-review:","Refund needs review","The refund needs manual reconciliation. No new refund will be created automatically.");
    }
    @Transactional public void apply(RefundGateway.Refund provider,String eventId) {
        // A scalar lookup avoids hydrating stale Booking state before acquiring locks.
        Long id=refunds.findBookingIdByKey(provider.key()).orElse(null);if(id==null)return;
        var b=locked(id,null);var p=payments.findLockedByBookingId(id).orElseThrow(HotelManagementService::missing);
        var r=refunds.findLockedByBookingId(id).orElseThrow(HotelManagementService::missing);
        boolean valid=provider.id()!=null&&provider.id().matches("re_[A-Za-z0-9_]+")&&"STAY".equals(provider.app())
            &&r.getRequestKey().equals(provider.key())&&id.toString().equals(provider.bookingId())
            &&r.getPaymentIntentId().equals(provider.paymentIntent())&&r.getPaymentIntentId().equals(p.getPaymentIntentId())
            &&r.getAmountMinor()==provider.amount()&&r.getCurrency().equalsIgnoreCase(provider.currency())
            &&(r.getRefundId()==null||r.getRefundId().equals(provider.id()))
            &&Set.of("pending","requires_action","succeeded","failed","canceled").contains(provider.status());
        if(!valid)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Stripe refund does not match this reservation. Reconciliation is required.");
        if(eventId!=null&&events.findLockedById(eventId).isPresent())return;
        if(!r.getStatus().equals("SUCCEEDED")) {
            if(provider.status().equals("succeeded")) {
                if(b.getStatus()!=BookingStatus.CONFIRMED)throw conflict("Refund succeeded but booking inventory needs manual reconciliation.");
                var nights=inventory.findNightsForUpdate(b.getRoomType().getId(),b.getCheckIn(),b.getCheckOut());
                if(nights.size()!=validateRange(b.getCheckIn(),b.getCheckOut())||nights.stream().anyMatch(n->n.getBookedRooms()<b.getQuantity()))
                    throw conflict("Refund succeeded but booking inventory needs manual reconciliation.");
                nights.forEach(n->n.release(b.getQuantity()));b.refunded();r.update(provider.id(),"SUCCEEDED");
                notifyBoth(b,"refund-completed:","Booking cancelled and refunded","The full sandbox refund succeeded. The booking is cancelled and its rooms have been released.");
            } else if(Set.of("failed","canceled").contains(provider.status())) {
                boolean first=!r.getStatus().equals("FAILED");r.update(provider.id(),"FAILED");b.refundFailed();
                if(first)notifyBoth(b,"refund-failed:","Refund failed","The refund failed. The booking and rooms remain reserved. Contact support.");
            } else if(provider.status().equals("requires_action")) {
                r.update(provider.id(),"REVIEW_REQUIRED");
                notifyBoth(b,"refund-review:","Refund needs review","The refund needs manual reconciliation. No new refund will be created automatically.");
            } else if(!r.getStatus().equals("FAILED")) r.update(provider.id(),"PENDING");
        }
        // The existing payment-event journal stores provider object IDs (Session or Refund).
        if(eventId!=null)events.save(new PaymentEvent(eventId,provider.id()));
    }
    private void notifyBoth(Booking b,String key,String title,String message) {
        notifications.eventOnce(b.getGuest().getId(),key+b.getId(),title,b.getHotelName()+" · "+message,"/bookings?booking="+b.getId());
        notifications.eventOnce(b.getRoomType().getHotel().getHost().getId(),key+b.getId(),title,b.getHotelName()+" · "+b.getRoomName()+" · "+message,"/host/bookings?hotelId="+b.getRoomType().getHotel().getId());
    }
}
