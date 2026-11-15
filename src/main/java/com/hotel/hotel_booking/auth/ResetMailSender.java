package com.hotel.hotel_booking.auth;

public interface ResetMailSender {
    boolean available();
    void send(String email, String resetLink);
}
