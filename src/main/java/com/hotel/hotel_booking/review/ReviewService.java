package com.hotel.hotel_booking.review;
import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.notification.NotificationService;
import jakarta.validation.constraints.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class ReviewService {
    private final ReviewRepository reviews;private final BookingRepository bookings;private final UserRepository users;
    private final HotelRepository hotels;private final HotelManagementService management;private final NotificationService notifications;
    public ReviewService(ReviewRepository reviews,BookingRepository bookings,UserRepository users,HotelRepository hotels,HotelManagementService management,NotificationService notifications){this.reviews=reviews;this.bookings=bookings;this.users=users;this.hotels=hotels;this.management=management;this.notifications=notifications;}
    public record Edit(@Min(1) @Max(5) int rating,@NotBlank @Size(max=2000) String comment) {}
    public record Reply(@NotBlank @Size(max=1000) String reply) {}
    public record View(Long id,String authorName,int rating,String comment,String roomName,Instant createdAt,Instant updatedAt,String hostReply,Instant repliedAt){static View from(HotelReview r){return new View(r.getId(),r.getBooking().getGuest().getName(),r.getRating(),r.getComment(),r.getBooking().getRoomName(),r.getCreatedAt(),r.getUpdatedAt(),r.getHostReply(),r.getRepliedAt());}}
    public record Summary(long reviewCount,Double averageRating) {}
    public record Listing(HotelDtos.PageResponse<View> reviews,Summary summary) {}
    public record Highlight(Long id,String authorName,int rating,String comment,Instant createdAt,Long hotelId,String hotelName,String city) {}
    public java.util.List<Highlight> highlights() {
        return reviews.latestPublic(org.springframework.data.domain.PageRequest.of(0,3)).stream()
            .map(row -> new Highlight(row.getId(),row.getAuthorName(),row.getRating(),row.getComment(),row.getCreatedAt(),row.getHotelId(),row.getHotelName(),row.getCity())).toList();
    }
    public record Eligibility(boolean eligible,String reason,View review) {}
    private String reason(Booking b){if(b.getStatus()!=BookingStatus.CONFIRMED)return "Only confirmed reservations can be reviewed.";if(LocalDate.now(ZoneId.of(b.getHotelTimeZone())).isBefore(b.getCheckOut()))return "You can review this booking from the hotel's local check-out date.";return "";}
    public Eligibility eligibility(String email,Long bookingId){var user=management.actor(email,Role.USER);var b=bookings.findByIdAndGuestId(bookingId,user.getId()).orElseThrow(HotelManagementService::missing);String reason=reason(b);return new Eligibility(reason.isEmpty(),reason,reviews.findByBookingId(b.getId()).map(View::from).orElse(null));}
    private Listing list(Long hotelId,int page,int size){var s=reviews.stats(hotelId);Double average=s.getAverageRating()==null?null:Math.round(s.getAverageRating()*10)/10.0;return new Listing(HotelDtos.PageResponse.from(reviews.findByBookingRoomTypeHotelId(hotelId,page(page,size)).map(View::from)),new Summary(s.getReviewCount(),average));}
    public Listing publicList(Long hotelId,int page,int size){management.published(hotelId);return list(hotelId,page,size);}
    public Listing hostList(String email,Long hotelId,int page,int size){management.ownedHotel(hotelId,email,false);return list(hotelId,page,size);}
    @Transactional public View save(String email,Long bookingId,Edit in){
        var user=users.findLockedByEmail(email).orElseThrow(HotelManagementService::missing);if(user.getRole()!=Role.USER)throw new SecurityException();
        var existingBooking=bookings.findByIdAndGuestId(bookingId,user.getId()).orElseThrow(HotelManagementService::missing);
        var hotel=hotels.findLockedById(existingBooking.getRoomType().getHotel().getId()).orElseThrow(HotelManagementService::missing);
        var b=bookings.findOwnedForUpdate(bookingId,user.getId()).orElseThrow(HotelManagementService::missing);String reason=reason(b);if(!reason.isEmpty())throw conflict(reason);
        var review=reviews.findLockedByBookingId(bookingId).orElse(null);boolean created=review==null;
        if(created)review=new HotelReview(b,in.rating(),in.comment());else review.edit(in.rating(),in.comment());
        reviews.saveAndFlush(review);
        if(created)notifications.event(hotel.getHost().getId(),"review-created:"+review.getId(),"New guest review",hotel.getName()+" received a "+in.rating()+"/5 review.","/host/reviews?hotelId="+hotel.getId());
        return View.from(review);
    }
    @Transactional public View reply(String email,Long hotelId,Long reviewId,Reply in){
        var hotel=management.ownedHotel(hotelId,email,true);if(hotel.getStatus()==HotelStatus.ARCHIVED)throw conflict("Archived hotels cannot be edited.");
        var review=reviews.findLockedById(reviewId).orElseThrow(HotelManagementService::missing);if(!review.getBooking().getRoomType().getHotel().getId().equals(hotelId))throw missing();
        boolean first=review.getHostReply()==null;review.reply(in.reply());
        if(first)notifications.event(review.getBooking().getGuest().getId(),"review-replied:"+reviewId,"Host replied to your review",hotel.getName()+" replied to your hotel review.","/bookings?booking="+review.getBooking().getId());
        return View.from(review);
    }
}
