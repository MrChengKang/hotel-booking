package com.hotel.hotel_booking.payment;
import java.time.Instant;
public interface StripeGateway {
    record Request(Long bookingId,String attemptKey,long amount,String currency,String name,Instant expiresAt) {}
    record Session(String id,String url,String status,String paymentStatus,long amount,String currency,String reference,String attemptKey,String paymentIntent,Instant expiresAt,boolean livemode,String app) {}
    Session create(Request request);
    Session retrieve(String sessionId);
    Session expire(String sessionId);
}
