package com.hotel.hotel_booking.payment;
import com.hotel.hotel_booking.booking.Booking;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="stay_payment_attempts",uniqueConstraints={@UniqueConstraint(name="uk_stay_payment_booking",columnNames="booking_id"),@UniqueConstraint(name="uk_stay_payment_key",columnNames="attempt_key"),@UniqueConstraint(name="uk_stay_payment_session",columnNames="session_id")})
public class PaymentAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="booking_id",nullable=false) private Booking booking;
    @Column(nullable=false,length=36) private String attemptKey=UUID.randomUUID().toString();
    @Column(nullable=false) private long amountMinor;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=16) private String status="INITIALIZING";
    @Column(length=255) private String sessionId;
    @Column(length=2048) private String sessionUrl;
    @Column(length=255) private String paymentIntentId;
    @Column(nullable=false) private Instant requestedExpiresAt;
    @Column(nullable=false) private boolean cancellationRequested;
    @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
    private Instant paidAt;
    protected PaymentAttempt() {}
    public PaymentAttempt(Booking b,Instant expires){booking=b;amountMinor=PaymentSettings.minor(b.getTotal(),b.getCurrencyCode());currency=b.getCurrencyCode();requestedExpiresAt=expires;}
    public Long getId(){return id;} public Booking getBooking(){return booking;} public String getAttemptKey(){return attemptKey;} public long getAmountMinor(){return amountMinor;} public String getCurrency(){return currency;} public String getStatus(){return status;} public String getSessionId(){return sessionId;} public String getSessionUrl(){return sessionUrl;} public Instant getRequestedExpiresAt(){return requestedExpiresAt;} public boolean isCancellationRequested(){return cancellationRequested;}
    public void requestCancellation(){cancellationRequested=true;}
    public String getPaymentIntentId(){return paymentIntentId;}
    public Instant getCreatedAt(){return createdAt;} public Instant getPaidAt(){return paidAt;}
    public void attach(StripeGateway.Session s){sessionId=s.id();sessionUrl=s.url();if(!status.equals("PAID")&&!status.equals("EXPIRED"))status="OPEN";}
    public void paid(StripeGateway.Session s){attach(s);status="PAID";paymentIntentId=s.paymentIntent();paidAt=Instant.now();sessionUrl=null;}
    public void expired(){status="EXPIRED";sessionUrl=null;}
    public StripeGateway.Request request(){return new StripeGateway.Request(booking.getId(),attemptKey,amountMinor,currency,booking.getHotelName()+" · "+booking.getRoomName(),requestedExpiresAt);}
}
