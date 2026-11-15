package com.hotel.hotel_booking.payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,Long> {
    Optional<PaymentAttempt> findByBookingId(Long bookingId);
    Optional<PaymentAttempt> findByAttemptKey(String attemptKey);
    @Query("select p.booking.id from PaymentAttempt p where p.attemptKey=:key") Optional<Long> findBookingIdByAttemptKey(String key);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.booking.id=:bookingId")
    Optional<PaymentAttempt> findLockedByBookingId(Long bookingId);
}
