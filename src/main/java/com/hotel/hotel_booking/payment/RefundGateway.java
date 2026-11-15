package com.hotel.hotel_booking.payment;

public interface RefundGateway {
    record Request(Long bookingId, String key, String paymentIntent, long amount, String currency) {}
    record Refund(String id, String paymentIntent, long amount, String currency, String status,
                  String key, String bookingId, String app) {}
    Refund create(Request request);
    Refund retrieve(String refundId);
}
