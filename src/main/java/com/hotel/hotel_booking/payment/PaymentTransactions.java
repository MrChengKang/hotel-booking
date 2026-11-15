package com.hotel.hotel_booking.payment;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.notification.NotificationService;
import java.time.*;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class PaymentTransactions {
    private final BookingRepository bookings;private final UserRepository users;private final HotelRepository hotels;private final RoomTypeRepository rooms;private final RoomInventoryRepository inventory;
    private final PaymentAttemptRepository attempts;private final PaymentEventRepository events;private final NotificationService notifications;private final PaymentSettings settings;
    public PaymentTransactions(BookingRepository bookings,UserRepository users,HotelRepository hotels,RoomTypeRepository rooms,RoomInventoryRepository inventory,PaymentAttemptRepository attempts,PaymentEventRepository events,NotificationService notifications,PaymentSettings settings){this.bookings=bookings;this.users=users;this.hotels=hotels;this.rooms=rooms;this.inventory=inventory;this.attempts=attempts;this.events=events;this.notifications=notifications;this.settings=settings;}
    public record View(Long bookingId,BookingStatus bookingStatus,String paymentStatus,Instant expiresAt,boolean checkoutAvailable,boolean cancellationRequested) {}
    public record Plan(Long bookingId,StripeGateway.Request request,String sessionId,boolean cancellationRequested,Instant expiresAt) {}
    private Booking owned(String email,Long id){var u=users.findByEmail(email).orElseThrow(HotelManagementService::missing);if(u.getRole()!=Role.USER)throw new SecurityException();return bookings.findByIdAndGuestId(id,u.getId()).orElseThrow(HotelManagementService::missing);}
    // All inventory writers use the same lock order: guest -> hotel -> room -> booking -> payment -> nights.
    private Booking locked(Long id,String owner){var scope=bookings.scope(id).orElseThrow(HotelManagementService::missing);var user=users.findLockedById(scope.getGuestId()).orElseThrow(HotelManagementService::missing);if(owner!=null&&!user.getEmail().equals(owner))throw missing();hotels.findLockedById(scope.getHotelId()).orElseThrow(HotelManagementService::missing);rooms.findLockedById(scope.getRoomId()).orElseThrow(HotelManagementService::missing);return bookings.findLockedById(id).orElseThrow(HotelManagementService::missing);}
    public View view(String email,Long id){var b=owned(email,id);var a=attempts.findByBookingId(id).orElse(null);return new View(id,b.getStatus(),b.getPaymentStatus(),b.getExpiresAt(),settings.ready()&&b.getStatus()==BookingStatus.PENDING_PAYMENT&&(a==null||!a.isCancellationRequested()),a!=null&&a.isCancellationRequested());}
    public Plan plan(Long id){var b=bookings.findById(id).orElseThrow(HotelManagementService::missing);if(b.getStatus()!=BookingStatus.PENDING_PAYMENT)return null;var a=attempts.findByBookingId(id).orElse(null);return a==null?new Plan(id,null,null,false,b.getExpiresAt()):new Plan(id,a.request(),a.getSessionId(),a.isCancellationRequested(),b.getExpiresAt());}
    @Transactional public Plan prepare(String email,Long id){
        var b=locked(id,email);if(b.getStatus()!=BookingStatus.PENDING_PAYMENT)throw conflict("This reservation is not awaiting payment.");var a=attempts.findLockedByBookingId(id).orElse(null);
        if(a==null){if(!Instant.now().isBefore(b.getExpiresAt())){release(b,false);return null;}Instant expiry=b.getExpiresAt().isAfter(Instant.now().plusSeconds(31*60))?b.getExpiresAt():Instant.now().plusSeconds(31*60);expiry=Instant.ofEpochSecond(expiry.getEpochSecond());b.extendHold(expiry);a=attempts.saveAndFlush(new PaymentAttempt(b,expiry));}
        if(a.isCancellationRequested())throw conflict("Cancellation is being reconciled. This checkout cannot be resumed.");
        return new Plan(id,a.request(),a.getSessionId(),false,b.getExpiresAt());
    }
    @Transactional public boolean releaseWithoutAttempt(Long id,String email,boolean cancelled){var b=locked(id,email);if(b.getStatus()!=BookingStatus.PENDING_PAYMENT)return true;if(attempts.findLockedByBookingId(id).isPresent())return false;if(cancelled||!Instant.now().isBefore(b.getExpiresAt()))release(b,cancelled);return true;}
    @Transactional public void requestCancellation(String email,Long id){var b=locked(id,email);if(b.getStatus()!=BookingStatus.PENDING_PAYMENT)return;attempts.findLockedByBookingId(id).ifPresent(PaymentAttempt::requestCancellation);}
    private void release(Booking b,boolean cancelled){
        var nights=inventory.findNightsForUpdate(b.getRoomType().getId(),b.getCheckIn(),b.getCheckOut());if(nights.size()!=validateRange(b.getCheckIn(),b.getCheckOut())||nights.stream().anyMatch(n->n.getBookedRooms()<b.getQuantity()))throw conflict("Inventory needs reconciliation before releasing this room hold.");
        nights.forEach(n->n.release(b.getQuantity()));if(cancelled)b.cancel();else b.expire();
        notifications.event(b.getGuest().getId(),"hold-released:"+b.getId(),cancelled?"Reservation cancelled":"Payment window expired",b.getHotelName()+" · Your unpaid room hold has been released.","/bookings?booking="+b.getId());
    }
    private void validate(PaymentAttempt a,StripeGateway.Session s){
        boolean valid=!s.livemode()&&"STAY".equals(s.app())&&s.id()!=null&&s.id().matches("cs_test_[A-Za-z0-9_]+")&&a.getAttemptKey().equals(s.attemptKey())&&a.getBooking().getId().toString().equals(s.reference())&&a.getAmountMinor()==s.amount()&&a.getCurrency().equalsIgnoreCase(s.currency())&&s.expiresAt()!=null&&s.expiresAt().getEpochSecond()==a.getRequestedExpiresAt().getEpochSecond();
        if(a.getSessionId()!=null&&!a.getSessionId().equals(s.id()))valid=false;
        if(!java.util.Set.of("open","complete","expired").contains(s.status()))valid=false;
        if("paid".equals(s.paymentStatus())&&(!"complete".equals(s.status())||s.paymentIntent()==null||!s.paymentIntent().matches("pi_[A-Za-z0-9_]+")))valid=false;
        if("open".equals(s.status())){try{var uri=URI.create(s.url());if(!"https".equals(uri.getScheme())||!"checkout.stripe.com".equals(uri.getHost())||uri.getUserInfo()!=null)valid=false;}catch(Exception e){valid=false;}}
        if(!valid)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Stripe checkout does not match this reservation. Payment reconciliation is required.");
    }
    @Transactional public void apply(StripeGateway.Session session,String eventId){
        var bookingId=attempts.findBookingIdByAttemptKey(session.attemptKey()).orElse(null);if(bookingId==null)return;
        var b=locked(bookingId,null);var a=attempts.findLockedByBookingId(b.getId()).orElseThrow(HotelManagementService::missing);validate(a,session);
        if(eventId!=null&&events.findLockedById(eventId).isPresent())return;
        if("PAID".equals(a.getStatus())&&!"paid".equals(session.paymentStatus()))throw conflict("A stale payment update cannot replace a paid transaction.");
        a.attach(session);
        if("complete".equals(session.status())&&"paid".equals(session.paymentStatus())){
            a.paid(session);
            if(b.getStatus()==BookingStatus.PENDING_PAYMENT){b.paymentConfirmed();notifications.booking(b,false);}
            else if(b.getStatus()!=BookingStatus.CONFIRMED&&!b.getPaymentStatus().equals("REVIEW_REQUIRED")&&!b.getPaymentStatus().equals("REFUNDED")){b.paymentNeedsReview();notifications.event(b.getGuest().getId(),"payment-review:"+b.getId(),"Payment needs review","A sandbox payment was received for a closed reservation. Contact support.","/bookings?booking="+b.getId());notifications.event(b.getRoomType().getHotel().getHost().getId(),"payment-review:"+b.getId(),"Payment needs review",b.getHotelName()+" · A closed reservation needs payment reconciliation.","/host/bookings?hotelId="+b.getRoomType().getHotel().getId());}
        }else if("expired".equals(session.status())&&"unpaid".equals(session.paymentStatus())){a.expired();if(b.getStatus()==BookingStatus.PENDING_PAYMENT)release(b,a.isCancellationRequested());}
        if(eventId!=null)events.save(new PaymentEvent(eventId,session.id()));
    }
    public String checkoutUrl(String email,Long id){owned(email,id);var a=attempts.findByBookingId(id).orElseThrow(HotelManagementService::missing);return !a.isCancellationRequested()&&"OPEN".equals(a.getStatus())?a.getSessionUrl():null;}
    public BookingDtos.Detail detail(String email,Long id){return BookingDtos.Detail.from(owned(email,id));}
}
