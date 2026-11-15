package com.hotel.hotel_booking.notification;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="stay_notifications",uniqueConstraints=@UniqueConstraint(name="uk_stay_notification_event",columnNames={"recipient_id","event_key"}),indexes=@Index(name="ix_stay_notification_inbox",columnList="recipient_id,created_at"))
public class AccountNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long recipientId;
    @Column(nullable=false,length=100) private String eventKey;
    @Column(nullable=false,length=60) private String title;
    @Column(nullable=false,length=500) private String message;
    @Column(nullable=false,length=180) private String target;
    @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
    private Instant readAt;
    protected AccountNotification() {}
    public AccountNotification(Long recipientId,String eventKey,String title,String message,String target){this.recipientId=recipientId;this.eventKey=eventKey;this.title=title;this.message=message;this.target=target;}
    public Long getId(){return id;} public String getTitle(){return title;} public String getMessage(){return message;} public String getTarget(){return target;} public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;}
}
