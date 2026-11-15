package com.hotel.hotel_booking.notification;
import com.hotel.hotel_booking.user.UserRepository;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.booking.Booking;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class NotificationService {
    private final NotificationRepository notifications;private final UserRepository users;
    public NotificationService(NotificationRepository notifications,UserRepository users){this.notifications=notifications;this.users=users;}
    public record View(Long id,String title,String message,String target,Instant createdAt,Instant readAt){static View from(AccountNotification n){return new View(n.getId(),n.getTitle(),n.getMessage(),n.getTarget(),n.getCreatedAt(),n.getReadAt());}}
    public record Count(long unreadCount) {}
    private Long recipient(String email){return users.findByEmail(email).orElseThrow(HotelManagementService::missing).getId();}
    public HotelDtos.PageResponse<View> list(String email,int page,int size){return HotelDtos.PageResponse.from(notifications.findByRecipientId(recipient(email),page(page,size)).map(View::from));}
    public Count count(String email){return new Count(notifications.countByRecipientIdAndReadAtIsNull(recipient(email)));}
    @Transactional public void read(String email,Long id){Long recipient=recipient(email);if(!notifications.existsByIdAndRecipientId(id,recipient))throw missing();notifications.markRead(id,recipient,Instant.now());}
    @Transactional public void readAll(String email){notifications.markAll(recipient(email),Instant.now());}
    // Events are inserted in the caller's transaction: a failed booking/review creates no notification.
    @Transactional(propagation=Propagation.MANDATORY)
    public void event(Long recipientId,String key,String title,String message,String target){notifications.save(new AccountNotification(recipientId,key,title,message,target));}
    // Refund writers hold the same booking lock before checking their event keys.
    @Transactional(propagation=Propagation.MANDATORY)
    public void eventOnce(Long recipientId,String key,String title,String message,String target){if(!notifications.existsByRecipientIdAndEventKey(recipientId,key))event(recipientId,key,title,message,target);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void booking(Booking b,boolean cancelled){
        String key=(cancelled?"booking-cancelled:":"booking-confirmed:")+b.getId();
        event(b.getGuest().getId(),key,cancelled?"Booking cancelled":"Booking confirmed",b.getHotelName()+" · "+b.getCheckIn()+" – "+b.getCheckOut()+(cancelled?". Reserved rooms released.":(b.getPaymentStatus().equals("PAID")?". Sandbox payment received; your reservation is confirmed.":b.getPaymentStatus().equals("NOT_REQUIRED")?". Your reservation is confirmed; no payment is required.":". Your reservation is confirmed and remains unpaid.")),"/bookings?booking="+b.getId());
        event(b.getRoomType().getHotel().getHost().getId(),key,cancelled?"Guest cancelled a booking":"New hotel booking",b.getHotelName()+" · "+b.getRoomName()+" · "+b.getCheckIn()+" – "+b.getCheckOut(),"/host/bookings?hotelId="+b.getRoomType().getHotel().getId());
    }
}
