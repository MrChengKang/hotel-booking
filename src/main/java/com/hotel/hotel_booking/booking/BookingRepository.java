package com.hotel.hotel_booking.booking;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking,Long>, JpaSpecificationExecutor<Booking> {
    @Override
    @EntityGraph(attributePaths={"guest","roomType","roomType.hotel","roomType.hotel.host"})
    Page<Booking> findAll(org.springframework.data.jpa.domain.Specification<Booking> specification,Pageable pageable);
    @EntityGraph(attributePaths={"guest","roomType","roomType.hotel","roomType.hotel.host","nights"})
    @Query("select b from Booking b where b.id=:id")
    Optional<Booking> findAdminById(@Param("id") Long id);
    long countByStatus(BookingStatus status);
    long countByPaymentStatus(String status);
    long countByPaymentStatusIn(java.util.Collection<String> statuses);
    interface Scope {Long getGuestId();Long getRoomId();Long getHotelId();}
    @Query("select b.guest.id as guestId,b.roomType.id as roomId,b.roomType.hotel.id as hotelId from Booking b where b.id=:id")
    Optional<Scope> scope(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Booking b where b.id=:id") Optional<Booking> findLockedById(Long id);
    @Query("select b.id from Booking b where b.status=com.hotel.hotel_booking.booking.BookingStatus.PENDING_PAYMENT order by b.id")
    java.util.List<Long> pendingIds(Pageable page);
    @Query("select b.id from Booking b where b.status=com.hotel.hotel_booking.booking.BookingStatus.PENDING_PAYMENT and b.id>:after order by b.id")
    java.util.List<Long> pendingIdsAfter(long after,Pageable page);
    long countByRoomTypeHotelHostId(Long hostId);
    long countByRoomTypeHotelHostIdAndStatus(Long hostId, BookingStatus status);
    Optional<Booking> findByGuestIdAndRequestKey(Long guestId,String requestKey);
    Optional<Booking> findByIdAndGuestId(Long id,Long guestId);
    Page<Booking> findByGuestId(Long guestId,Pageable page);
    Page<Booking> findByRoomTypeHotelId(Long hotelId,Pageable page);
    @Query("select b.roomType.id from Booking b where b.id=:id and b.guest.id=:guestId")
    Optional<Long> findOwnedRoomId(@Param("id") Long id,@Param("guestId") Long guestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id=:id and b.guest.id=:guestId")
    Optional<Booking> findOwnedForUpdate(@Param("id") Long id,@Param("guestId") Long guestId);
}
