package com.hotel.hotel_booking.hotel;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    long countByHotelIdAndActiveTrue(Long hotelId);
    List<RoomType> findByHotelIdOrderById(Long hotelId);
    @Query("select r.hotel.id from RoomType r where r.id = :id")
    Optional<Long> findHotelId(@Param("id") Long id);
    List<RoomType> findByHotelIdAndActiveTrueOrderById(Long hotelId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomType r join fetch r.hotel h join fetch h.host where r.id = :id")
    Optional<RoomType> findLockedById(@Param("id") Long id);
}
