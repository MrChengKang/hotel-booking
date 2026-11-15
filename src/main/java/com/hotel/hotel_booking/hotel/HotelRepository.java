package com.hotel.hotel_booking.hotel;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    interface DestinationSummary { String getCity(); long getHotelCount(); }
    @org.springframework.data.jpa.repository.Query("select min(h.city) as city, count(h) as hotelCount from Hotel h where h.status = com.hotel.hotel_booking.hotel.HotelStatus.PUBLISHED group by lower(h.city) order by count(h) desc, lower(h.city) asc")
    List<DestinationSummary> destinationSummary(org.springframework.data.domain.Pageable pageable);
    long countByStatus(HotelStatus status);
    long countByReviewStatus(String status);
    @org.springframework.data.jpa.repository.Query(value="select h from Hotel h join fetch h.host owner where (:review is null or h.reviewStatus=:review) and (:search is null or lower(h.name) like :search escape '!' or lower(h.city) like :search escape '!' or lower(owner.email) like :search escape '!')",
        countQuery="select count(h) from Hotel h join h.host owner where (:review is null or h.reviewStatus=:review) and (:search is null or lower(h.name) like :search escape '!' or lower(h.city) like :search escape '!' or lower(owner.email) like :search escape '!')")
    org.springframework.data.domain.Page<Hotel> adminBrowse(String review,String search,org.springframework.data.domain.Pageable page);
    long countByHostId(Long hostId);
    long countByHostIdAndStatus(Long hostId, HotelStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select h from Hotel h join fetch h.host where h.id = :id")
    Optional<Hotel> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Hotel> findByIdAndStatus(Long id,HotelStatus status);
    org.springframework.data.domain.Page<Hotel> findByHostId(Long hostId,org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select h from Hotel h where h.status = com.hotel.hotel_booking.hotel.HotelStatus.PUBLISHED and (:city is null or lower(h.city) = lower(:city))")
    org.springframework.data.domain.Page<Hotel> browse(@org.springframework.data.repository.query.Param("city") String city,org.springframework.data.domain.Pageable pageable);
    Optional<Hotel> findBySlug(String slug);
    List<Hotel> findByHostIdOrderByIdDesc(Long hostId);
}
