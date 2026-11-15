package com.hotel.hotel_booking.admin;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.HotelManagementService;
import com.hotel.hotel_booking.hotel.HotelDtos.PageResponse;
import com.hotel.hotel_booking.payment.*;
import com.hotel.hotel_booking.user.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform inspection only: never reconciles or mutates a payment or reservation. */
@Service @Transactional(readOnly=true)
public class AdminBookingService {
    private static final Set<String> PAYMENT_STATUSES=Set.of("UNPAID","PAID","NOT_REQUIRED","REVIEW_REQUIRED","REFUND_PENDING","REFUND_FAILED","REFUNDED");
    private static final Set<String> REFUND_STATUSES=Set.of("INITIALIZING","PENDING","SUCCEEDED","FAILED","REVIEW_REQUIRED");
    private final HotelManagementService management;
    private final BookingRepository bookings;
    private final PaymentAttemptRepository payments;
    private final RefundAttemptRepository refunds;

    public AdminBookingService(HotelManagementService management,BookingRepository bookings,
        PaymentAttemptRepository payments,RefundAttemptRepository refunds){
        this.management=management;this.bookings=bookings;this.payments=payments;this.refunds=refunds;
    }

    public record Contact(Long id,String name,String email){
        static Contact from(User user){return new Contact(user.getId(),user.getName(),user.getEmail());}
    }
    public record PaymentView(Long id,String status,long amountMinor,String currencyCode,String sessionId,
        String paymentIntentId,Instant createdAt,Instant paidAt,Instant expiresAt,boolean cancellationRequested){
        static PaymentView from(PaymentAttempt payment){return new PaymentView(payment.getId(),payment.getStatus(),
            payment.getAmountMinor(),payment.getCurrency(),payment.getSessionId(),payment.getPaymentIntentId(),
            payment.getCreatedAt(),payment.getPaidAt(),payment.getRequestedExpiresAt(),payment.isCancellationRequested());}
    }
    public record RefundView(Long id,String status,long amountMinor,String currencyCode,String refundId,
        String paymentIntentId,String policyVersion,Instant createdAt,Instant updatedAt){
        static RefundView from(RefundAttempt refund){return new RefundView(refund.getId(),refund.getStatus(),
            refund.getAmountMinor(),refund.getCurrency(),refund.getRefundId(),refund.getPaymentIntentId(),
            refund.getPolicyVersion(),refund.getCreatedAt(),refund.getUpdatedAt());}
    }
    public record BookingItem(BookingDtos.Summary booking,Contact guest,Contact host,String refundStatus){}
    public record Detail(BookingDtos.Summary booking,Contact guest,Contact host,List<BookingDtos.Night> nights,
        PaymentView payment,RefundView refund,String paymentMode){}
    public record RefundItem(BookingDtos.Summary booking,Contact guest,Contact host,RefundView refund){}

    /** Shared booking predicates deliberately use snapshot hotelName, not mutable hotel metadata. */
    private record Filter(String search,String hotel,Long hotelId,LocalDate checkInFrom,LocalDate checkInTo){}
    private Filter filter(String search,String hotel,Long hotelId,LocalDate from,LocalDate to){
        if(hotelId!=null&&hotelId<=0)throw new IllegalArgumentException("Hotel ID must be positive.");
        if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("Check-in date range is invalid.");
        return new Filter(literalContains(search,254),literalContains(hotel,160),hotelId,from,to);
    }
    private String literalContains(String value,int maximum){
        if(value==null||value.isBlank())return null;
        if(value.length()>maximum)throw new IllegalArgumentException("Search filter is too long.");
        return "%"+value.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    private String optional(String value){return value==null||value.isBlank()?null:value.strip();}
    private BookingStatus bookingStatus(String value){
        String status=optional(value);return status==null?null:BookingStatus.valueOf(status);
    }
    private String paymentStatus(String value){
        String status=optional(value);
        if(status!=null&&!PAYMENT_STATUSES.contains(status))throw new IllegalArgumentException("Invalid payment status.");
        return status;
    }
    private Set<String> refundStatuses(String value){
        String status=optional(value);
        if(status==null)return null;
        if(status.equals("NEEDS_REVIEW"))return Set.of("FAILED","REVIEW_REQUIRED");
        if(status.equals("PROCESSING"))return Set.of("INITIALIZING","PENDING");
        if(!REFUND_STATUSES.contains(status))throw new IllegalArgumentException("Invalid refund status.");
        return Set.of(status);
    }
    private List<Predicate> bookingPredicates(From<?,Booking> booking,CriteriaBuilder cb,Filter filter){
        var predicates=new ArrayList<Predicate>();
        if(filter.search()!=null){
            var guest=booking.join("guest",JoinType.INNER);
            predicates.add(cb.or(cb.like(cb.lower(booking.get("reference")),filter.search(),'!'),
                cb.like(cb.lower(guest.get("email")),filter.search(),'!'),
                cb.like(cb.lower(guest.get("name")),filter.search(),'!')));
        }
        if(filter.hotel()!=null)predicates.add(cb.like(cb.lower(booking.get("hotelName")),filter.hotel(),'!'));
        if(filter.hotelId()!=null)predicates.add(cb.equal(booking.get("roomType").get("hotel").get("id"),filter.hotelId()));
        if(filter.checkInFrom()!=null)predicates.add(cb.greaterThanOrEqualTo(booking.get("checkIn"),filter.checkInFrom()));
        if(filter.checkInTo()!=null)predicates.add(cb.lessThanOrEqualTo(booking.get("checkIn"),filter.checkInTo()));
        return predicates;
    }
    private Contact host(Booking booking){return Contact.from(booking.getRoomType().getHotel().getHost());}

    public PageResponse<BookingItem> bookings(String email,String search,String hotel,Long hotelId,
        String status,String paymentStatus,LocalDate from,LocalDate to,int page,int size){
        management.actor(email,Role.ADMIN);
        var filter=filter(search,hotel,hotelId,from,to);var state=bookingStatus(status);var payment=paymentStatus(paymentStatus);
        Specification<Booking> specification=(root,query,cb)->{
            var predicates=bookingPredicates(root,cb,filter);
            if(state!=null)predicates.add(cb.equal(root.get("status"),state));
            if(payment!=null)predicates.add(cb.equal(root.get("paymentStatus"),payment));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result=bookings.findAll(specification,HotelManagementService.page(page,size));
        var ids=result.getContent().stream().map(Booking::getId).toList();
        // A single bounded batch lookup instead of one refund SELECT per reservation.
        Map<Long,String> refundStatus=ids.isEmpty()?Map.of():refunds.findByBookingIdIn(ids).stream()
            .collect(Collectors.toMap(refund->refund.getBooking().getId(),RefundAttempt::getStatus));
        return PageResponse.from(result.map(booking->new BookingItem(BookingDtos.Summary.from(booking),
            Contact.from(booking.getGuest()),host(booking),refundStatus.getOrDefault(booking.getId(),"NOT_REQUESTED"))));
    }
    public Detail detail(String email,Long id){
        management.actor(email,Role.ADMIN);
        if(id==null||id<=0)throw new IllegalArgumentException("Booking ID must be positive.");
        var booking=bookings.findAdminById(id).orElseThrow(HotelManagementService::missing);
        var payment=payments.findByBookingId(id).map(PaymentView::from).orElse(null);
        var refund=refunds.findByBookingId(id).map(RefundView::from).orElse(null);
        return new Detail(BookingDtos.Summary.from(booking),Contact.from(booking.getGuest()),host(booking),
            booking.getNights().stream().map(night->new BookingDtos.Night(night.getStayDate(),night.getUnitPrice())).toList(),
            payment,refund,"test");
    }
    public PageResponse<RefundItem> refunds(String email,String search,String hotel,Long hotelId,String status,
        LocalDate from,LocalDate to,int page,int size){
        management.actor(email,Role.ADMIN);
        var filter=filter(search,hotel,hotelId,from,to);var statuses=refundStatuses(status);
        Specification<RefundAttempt> specification=(root,query,cb)->{
            Join<RefundAttempt,Booking> booking=root.join("booking",JoinType.INNER);
            var predicates=bookingPredicates(booking,cb,filter);
            if(statuses!=null)predicates.add(root.get("status").in(statuses));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(refunds.findAll(specification,HotelManagementService.page(page,size)).map(refund->{
            var booking=refund.getBooking();return new RefundItem(BookingDtos.Summary.from(booking),
                Contact.from(booking.getGuest()),host(booking),RefundView.from(refund));
        }));
    }
}
