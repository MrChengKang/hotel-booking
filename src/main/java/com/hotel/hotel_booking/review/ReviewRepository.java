package com.hotel.hotel_booking.review;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ReviewRepository extends JpaRepository<HotelReview,Long> {
    Optional<HotelReview> findByBookingId(Long bookingId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HotelReview r where r.booking.id=:bookingId")
    Optional<HotelReview> findLockedByBookingId(Long bookingId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HotelReview r where r.id=:id")
    Optional<HotelReview> findLockedById(Long id);
    @EntityGraph(attributePaths={"booking","booking.guest"})
    Page<HotelReview> findByBookingRoomTypeHotelId(Long hotelId,Pageable page);
    interface Stats {Double getAverageRating();long getReviewCount();}
    @Query("select avg(r.rating) as averageRating,count(r) as reviewCount from HotelReview r where r.booking.roomType.hotel.id=:hotelId")
    Stats stats(Long hotelId);
    interface HighlightRow {
        Long getId(); String getAuthorName(); int getRating(); String getComment();
        java.time.Instant getCreatedAt(); Long getHotelId(); String getHotelName(); String getCity();
    }
    @Query("select r.id as id, guest.name as authorName, r.rating as rating, r.comment as comment, r.createdAt as createdAt, h.id as hotelId, h.name as hotelName, h.city as city " +
        "from HotelReview r join r.booking b join b.guest guest join b.roomType room join room.hotel h " +
        "where h.status = com.hotel.hotel_booking.hotel.HotelStatus.PUBLISHED and b.status = com.hotel.hotel_booking.booking.BookingStatus.CONFIRMED " +
        "order by r.createdAt desc, r.id desc")
    java.util.List<HighlightRow> latestPublic(Pageable page);
}
