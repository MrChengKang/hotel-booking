package com.hotel.hotel_booking.admin;

import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.payment.RefundAttemptRepository;
import com.hotel.hotel_booking.notification.NotificationService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class AdminService {
    private final HotelManagementService management;
    private final HotelRepository hotels;
    private final UserRepository users;
    private final RoomTypeRepository rooms;
    private final HotelImageRepository images;
    private final BookingRepository bookings;
    private final RefundAttemptRepository refunds;
    private final NotificationService notifications;
    public AdminService(HotelManagementService management,HotelRepository hotels,UserRepository users,
        RoomTypeRepository rooms,HotelImageRepository images,BookingRepository bookings,
        RefundAttemptRepository refunds,NotificationService notifications){
        this.management=management;this.hotels=hotels;this.users=users;this.rooms=rooms;this.images=images;
        this.bookings=bookings;this.refunds=refunds;this.notifications=notifications;
    }
    public record Overview(long users,long hosts,long admins,long hotels,long pendingHotels,long publishedHotels,
        long bookings,long pendingPayments,long paidBookings,long refundedBookings,long refundsNeedingReview,String paymentMode) {}
    public record UserView(Long id,String name,String email,Role role,Instant createdAt){
        public static UserView from(User u){return new UserView(u.getId(),u.getName(),u.getEmail(),u.getRole(),u.getCreatedAt());}
    }
    public record HotelItem(HotelView hotel,UserView host,long activeRooms,long photos) {}
    public record Detail(HotelView hotel,UserView host,List<RoomView> rooms,List<HotelImageService.View> images) {}
    private User admin(String email){return management.actor(email,Role.ADMIN);}
    public Overview overview(String email){
        admin(email);return new Overview(users.count(),users.countByRole(Role.HOST),users.countByRole(Role.ADMIN),hotels.count(),
            hotels.countByReviewStatus("PENDING"),hotels.countByStatus(HotelStatus.PUBLISHED),bookings.count(),
            bookings.countByStatus(BookingStatus.PENDING_PAYMENT),bookings.countByPaymentStatusIn(Set.of("PAID","REFUND_PENDING","REFUND_FAILED")),
            bookings.countByPaymentStatus("REFUNDED"),refunds.countByStatusIn(Set.of("FAILED","REVIEW_REQUIRED")),"test");
    }
    private String search(String value){
        if(value==null||value.isBlank())return null;
        if(value.length()>100)throw new IllegalArgumentException("Search must be at most 100 characters.");
        return value.strip();
    }
    public PageResponse<UserView> users(String email,String search,int page,int size){
        admin(email);var term=search(search);var pageable=page(page,size);
        return PageResponse.from((term==null?users.findAll(pageable):users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term,term,pageable)).map(UserView::from));
    }
    private HotelView view(Hotel h){return HotelView.from(h,images.findFirstByHotelIdAndRoomTypeIsNullOrderByPositionAscIdAsc(h.getId()).map(i->"/api/media/"+i.getId()).orElse(null));}
    private RoomView roomView(RoomType r){return RoomView.from(r,images.findFirstByRoomTypeIdOrderByPositionAscIdAsc(r.getId()).map(i->"/api/media/"+i.getId()).orElse(null));}
    public PageResponse<HotelItem> hotels(String email,String review,String search,int page,int size){
        admin(email);
        if(review!=null&&review.isBlank())review=null;
        if(review!=null&&!Set.of("NOT_SUBMITTED","PENDING","APPROVED","REJECTED").contains(review))throw new IllegalArgumentException("Invalid review status.");
        var term=search(search);
        if(term!=null)term="%"+term.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return PageResponse.from(hotels.adminBrowse(review,term,page(page,size)).map(h->new HotelItem(view(h),UserView.from(h.getHost()),rooms.countByHotelIdAndActiveTrue(h.getId()),images.countByHotelId(h.getId()))));
    }
    public Detail detail(String email,Long id){
        admin(email);var h=hotels.findById(id).orElseThrow(HotelManagementService::missing);
        return new Detail(view(h),UserView.from(h.getHost()),rooms.findByHotelIdOrderById(id).stream().map(this::roomView).toList(),
            images.findByHotelIdOrderByPositionAscIdAsc(id).stream().map(HotelImageService.View::from).toList());
    }
    @Transactional public HotelView decide(String email,Long id,AdminController.Decision request){
        var actor=admin(email);var h=hotels.findLockedById(id).orElseThrow(HotelManagementService::missing);
        if(h.getVersion()!=request.expectedVersion()||h.getStatus()!=HotelStatus.DRAFT||!h.getReviewStatus().equals("PENDING"))
            throw conflict("This review changed. Refresh the hotel before deciding.");
        boolean approved=request.decision().equals("APPROVE");
        String note=request.note()==null?"":request.note().strip();
        if(!approved&&note.isEmpty())throw new IllegalArgumentException("A rejection reason is required.");
        if(approved)management.requireReviewReady(h);
        h.decideReview(approved,note,actor.getId());hotels.flush();
        notifications.event(h.getHost().getId(),"hotel-decision:"+id+":"+h.getVersion(),approved?"Hotel approved":"Hotel needs changes",
            h.getName()+ (approved?" is now published.":" was not approved. Open your hotels to see the review notes."),"/host/hotels");
        return view(h);
    }
}
