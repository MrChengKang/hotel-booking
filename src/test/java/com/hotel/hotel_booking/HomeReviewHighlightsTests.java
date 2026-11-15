package com.hotel.hotel_booking;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.review.*;
import com.hotel.hotel_booking.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:homehighlights;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @Transactional
class HomeReviewHighlightsTests {
    @Autowired UserRepository users;
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired BookingRepository bookings;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    User host, guest;
    int sequence;

    @BeforeEach void setup() {
        host=users.save(new User("Host", "home-host@example.com", "private-hash", Role.HOST));
        guest=users.save(new User("Real Guest", "private-guest@example.com", "private-hash", Role.USER));
    }
    Hotel hotel() {
        var hotel=hotels.save(new Hotel(host,"Actual Hotel "+(++sequence),"home-hotel-"+sequence,"Description","1 Main Street","Raub","MY","MYR","Asia/Kuala_Lumpur"));
        hotel.publish();return hotels.saveAndFlush(hotel);
    }
    HotelReview review(Hotel hotel,int rating) {
        var room=rooms.saveAndFlush(new RoomType(hotel,"ROOM"+(++sequence),"Real Room","",2,1,2,new BigDecimal("100.00")));
        LocalDate checkout=LocalDate.now(ZoneOffset.UTC).minusDays(2);
        var booking=bookings.saveAndFlush(new Booking(guest,room,UUID.randomUUID().toString(),checkout.minusDays(1),checkout,1,2));
        return reviews.saveAndFlush(new HotelReview(booking,rating,"Actual comment "+sequence));
    }
    @Test void emptyDataNeverProducesSampleReviews() {
        assertTrue(service.highlights().isEmpty());
    }
    @Test void returnsTheLatestThreeWithoutCherryPickingRatings() {
        var hotel=hotel();
        var first=review(hotel,5);var second=review(hotel,4);var third=review(hotel,3);var newest=review(hotel,1);
        for(var item:List.of(first,second,third,newest))jdbc.update("update stay_hotel_reviews set created_at=? where id=?",Instant.parse("2026-01-01T00:00:00Z"),item.getId());
        var result=service.highlights();
        assertEquals(List.of(newest.getId(),third.getId(),second.getId()),result.stream().map(ReviewService.Highlight::id).toList());
        assertEquals(1,result.getFirst().rating());
    }
    @Test void hiddenHotelsAndCancelledOrPendingBookingsAreExcludedBeforeLimit() {
        var published=hotel();var visible=review(published,2);
        var draft=hotel();review(draft,5);draft.unpublish();hotels.saveAndFlush(draft);
        var archived=hotel();review(archived,5);archived.archive();hotels.saveAndFlush(archived);
        var cancelled=review(published,5);cancelled.getBooking().cancel();bookings.flush();
        var pending=review(published,5);pending.getBooking().hold(Instant.now().plusSeconds(100));bookings.flush();
        assertEquals(List.of(visible.getId()),service.highlights().stream().map(ReviewService.Highlight::id).toList());
    }
    @Test void usesCurrentPublishedHotelIdentityAndAllowsOneOrTwoReviews() {
        var hotel=hotel();var first=review(hotel,4);
        assertEquals(1,service.highlights().size());
        assertEquals(hotel.getId(),service.highlights().getFirst().hotelId());
        assertEquals(hotel.getName(),service.highlights().getFirst().hotelName());
        assertEquals("Raub",service.highlights().getFirst().city());
        var second=review(hotel,3);
        assertEquals(2,service.highlights().size());
    }
    @Test void exposesOnlyPublicReviewFields() throws Exception {
        var hotel=hotel();review(hotel,4);
        var serialized=json.writeValueAsString(service.highlights());
        assertTrue(serialized.contains("Real Guest"));assertTrue(serialized.contains("Actual comment"));
        assertFalse(serialized.contains("private-guest@example.com"));assertFalse(serialized.contains("private-hash"));
        assertFalse(serialized.contains("bookingId"));assertFalse(serialized.contains("guestId"));assertFalse(serialized.contains("reference"));
    }
    @Test void editsAreReflectedWithoutCreatingAnotherHighlight() {
        var item=review(hotel(),4);item.edit(2,"Updated actual comment");reviews.saveAndFlush(item);
        assertEquals(1,service.highlights().size());assertEquals(2,service.highlights().getFirst().rating());
        assertEquals("Updated actual comment",service.highlights().getFirst().comment());
    }
}
