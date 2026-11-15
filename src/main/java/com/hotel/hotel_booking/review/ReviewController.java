package com.hotel.hotel_booking.review;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.hotel.hotel_booking.review.ReviewService.*;
@RestController
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service){this.service=service;}
    @GetMapping("/api/hotels/review-highlights") public java.util.List<Highlight> highlights(){return service.highlights();}
    @GetMapping("/api/hotels/{hotelId}/reviews") public Listing publicList(@PathVariable Long hotelId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="5") int size){return service.publicList(hotelId,page,size);}
    @GetMapping("/api/bookings/{bookingId}/review") public Eligibility eligibility(Principal p,@PathVariable Long bookingId){return service.eligibility(p.getName(),bookingId);}
    @PutMapping("/api/bookings/{bookingId}/review") public View save(Principal p,@PathVariable Long bookingId,@Valid @RequestBody Edit in){return service.save(p.getName(),bookingId,in);}
    @GetMapping("/api/host/hotels/{hotelId}/reviews") public Listing own(Principal p,@PathVariable Long hotelId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return service.hostList(p.getName(),hotelId,page,size);}
    @PutMapping("/api/host/hotels/{hotelId}/reviews/{reviewId}/reply") public View reply(Principal p,@PathVariable Long hotelId,@PathVariable Long reviewId,@Valid @RequestBody Reply in){return service.reply(p.getName(),hotelId,reviewId,in);}
}
