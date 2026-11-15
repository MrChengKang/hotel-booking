package com.hotel.hotel_booking.notification;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface NotificationRepository extends JpaRepository<AccountNotification,Long> {
    Page<AccountNotification> findByRecipientId(Long recipientId,Pageable page);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
    boolean existsByIdAndRecipientId(Long id,Long recipientId);
    boolean existsByRecipientIdAndEventKey(Long recipientId,String eventKey);
    @Modifying @Query("update AccountNotification n set n.readAt=:time where n.id=:id and n.recipientId=:recipientId and n.readAt is null")
    int markRead(Long id,Long recipientId,Instant time);
    @Modifying @Query("update AccountNotification n set n.readAt=:time where n.recipientId=:recipientId and n.readAt is null")
    int markAll(Long recipientId,Instant time);
}
