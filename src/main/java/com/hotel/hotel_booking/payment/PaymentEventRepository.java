package com.hotel.hotel_booking.payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface PaymentEventRepository extends JpaRepository<PaymentEvent,String> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select e from PaymentEvent e where e.id=:id") Optional<PaymentEvent> findLockedById(String id);
}
