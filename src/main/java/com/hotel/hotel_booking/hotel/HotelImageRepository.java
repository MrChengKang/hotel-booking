package com.hotel.hotel_booking.hotel;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface HotelImageRepository extends JpaRepository<HotelImage,Long> {
    @org.springframework.data.jpa.repository.Query("select i from HotelImage i where i.hotel.status=com.hotel.hotel_booking.hotel.HotelStatus.PUBLISHED and lower(i.hotel.city)=lower(:city) and i.roomType is null order by i.hotel.id desc,i.position asc,i.id asc")
    java.util.List<HotelImage> destinationCover(@org.springframework.data.repository.query.Param("city") String city,org.springframework.data.domain.Pageable pageable);
    long countByHotelId(Long hotelId);
    long countByHotelIdAndRoomTypeIsNull(Long hotelId);
    List<HotelImage> findByHotelIdOrderByPositionAscIdAsc(Long hotelId);
    @Query("select i from HotelImage i where i.hotel.id=:hotelId and ((:roomId is null and i.roomType is null) or i.roomType.id=:roomId) order by i.position,i.id")
    List<HotelImage> scope(Long hotelId,Long roomId);
    Optional<HotelImage> findFirstByHotelIdAndRoomTypeIsNullOrderByPositionAscIdAsc(Long hotelId);
    Optional<HotelImage> findFirstByRoomTypeIdOrderByPositionAscIdAsc(Long roomTypeId);
}
