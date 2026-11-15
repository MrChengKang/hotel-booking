package com.hotel.hotel_booking.payment;

import com.hotel.hotel_booking.booking.Booking;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="stay_refund_attempts", uniqueConstraints={
    @UniqueConstraint(name="uk_stay_refund_booking", columnNames="booking_id"),
    @UniqueConstraint(name="uk_stay_refund_key", columnNames="request_key"),
    @UniqueConstraint(name="uk_stay_refund_provider", columnNames="refund_id")})
public class RefundAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="booking_id", nullable=false) private Booking booking;
    @Column(nullable=false, length=36) private String requestKey = UUID.randomUUID().toString();
    @Column(nullable=false, length=255) private String paymentIntentId;
    @Column(nullable=false) private long amountMinor;
    @Column(nullable=false, length=3) private String currency;
    @Column(nullable=false, length=20) private String status = "INITIALIZING";
    @Column(length=255) private String refundId;
    @Column(nullable=false, length=32) private String policyVersion = "FULL_BEFORE_CHECKIN_V1";
    @Column(nullable=false, updatable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
    protected RefundAttempt() {}
    public RefundAttempt(Booking booking, String paymentIntent, long amountMinor) {
        this.booking=booking; this.paymentIntentId=paymentIntent; this.amountMinor=amountMinor; this.currency=booking.getCurrencyCode();
    }
    public Long getId(){return id;} public Booking getBooking(){return booking;} public String getRequestKey(){return requestKey;}
    public String getStatus(){return status;} public String getRefundId(){return refundId;} public Instant getCreatedAt(){return createdAt;}
    public long getAmountMinor(){return amountMinor;} public String getCurrency(){return currency;}
    public String getPaymentIntentId(){return paymentIntentId;}
    public String getPolicyVersion(){return policyVersion;} public Instant getUpdatedAt(){return updatedAt;}
    public RefundGateway.Request request(){return new RefundGateway.Request(booking.getId(),requestKey,paymentIntentId,amountMinor,currency);}
    public void update(String refundId,String status){if(refundId!=null)this.refundId=refundId;this.status=status;updatedAt=Instant.now();}
}
