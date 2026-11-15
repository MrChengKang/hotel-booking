package com.hotel.hotel_booking.payment;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt,Long>, JpaSpecificationExecutor<RefundAttempt> {
    @Override
    @EntityGraph(attributePaths={"booking","booking.guest","booking.roomType","booking.roomType.hotel","booking.roomType.hotel.host"})
    org.springframework.data.domain.Page<RefundAttempt> findAll(org.springframework.data.jpa.domain.Specification<RefundAttempt> specification,org.springframework.data.domain.Pageable pageable);
    List<RefundAttempt> findByBookingIdIn(Collection<Long> bookingIds);
    long countByStatusIn(java.util.Collection<String> statuses);
    Optional<RefundAttempt> findByBookingId(Long bookingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RefundAttempt r where r.booking.id=:id")
    Optional<RefundAttempt> findLockedByBookingId(Long id);
    @Query("select r.booking.id from RefundAttempt r where r.requestKey=:key") Optional<Long> findBookingIdByKey(String key);
    @Query("select r.booking.id from RefundAttempt r where r.booking.id>:after and r.status in ('INITIALIZING','PENDING') order by r.booking.id")
    List<Long> pendingIdsAfter(long after, Pageable page);
}
