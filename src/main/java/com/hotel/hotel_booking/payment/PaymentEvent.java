package com.hotel.hotel_booking.payment;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="stay_payment_events")
public class PaymentEvent {
    @Id @Column(length=255) private String id;
    @Column(nullable=false,length=255) private String sessionId;
    @Column(nullable=false) private Instant processedAt=Instant.now();
    protected PaymentEvent() {}
    public PaymentEvent(String id,String sessionId){this.id=id;this.sessionId=sessionId;}
}
