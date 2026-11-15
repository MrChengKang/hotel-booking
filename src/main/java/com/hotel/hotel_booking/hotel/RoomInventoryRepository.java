package com.hotel.hotel_booking.hotel;

import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {
    @Query("select count(i) from RoomInventory i where i.roomType.hotel.id=:hotelId and i.roomType.active=true and i.stayDate>=:today and i.stopSell=false and i.totalRooms-i.bookedRooms-i.blockedRooms>0")
    long sellableFutureNights(Long hotelId,LocalDate today);
    Optional<RoomInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);
    @Query("select i from RoomInventory i where i.roomType.id = :roomTypeId and i.stayDate >= :checkIn and i.stayDate < :checkOut order by i.stayDate")
    List<RoomInventory> findNights(@Param("roomTypeId") Long roomTypeId, @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RoomInventory i where i.roomType.id = :roomTypeId and i.stayDate >= :checkIn and i.stayDate < :checkOut order by i.stayDate")
    List<RoomInventory> findNightsForUpdate(@Param("roomTypeId") Long roomTypeId, @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("""
        select i.roomType.id from RoomInventory i
        where i.roomType.hotel.id = :hotelId and i.roomType.hotel.status = com.hotel.hotel_booking.hotel.HotelStatus.PUBLISHED
          and i.roomType.active = true and i.roomType.maxGuests >= :guestsPerRoom
          and i.stayDate >= :checkIn and i.stayDate < :checkOut and i.stopSell = false
          and i.totalRooms - i.bookedRooms - i.blockedRooms >= :quantity
        group by i.roomType.id having count(i) = :nights order by i.roomType.id
        """)
    List<Long> findAvailableRoomTypeIds(@Param("hotelId") Long hotelId, @Param("checkIn") LocalDate checkIn,
        @Param("checkOut") LocalDate checkOut, @Param("quantity") int quantity,
        @Param("guestsPerRoom") int guestsPerRoom, @Param("nights") long nights);
}
