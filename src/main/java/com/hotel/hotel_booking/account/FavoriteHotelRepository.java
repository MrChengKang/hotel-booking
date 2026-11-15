package com.hotel.hotel_booking.account;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface FavoriteHotelRepository extends JpaRepository<FavoriteHotel,Long> {
    @Query("select f from FavoriteHotel f join fetch f.hotel where f.user.id=:userId order by f.createdAt desc,f.id desc")
    List<FavoriteHotel> forUser(Long userId);
    Optional<FavoriteHotel> findByUserIdAndHotelId(Long userId,Long hotelId);
    long countByUserId(Long userId);
}
