package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.mail.BrevoMailClient;

public final class BrevoResetMailSender implements ResetMailSender {
    private final BrevoMailClient client;
    public BrevoResetMailSender(BrevoMailClient client) { this.client=client; }
    @Override public boolean available() { return client.available(); }
    @Override public void send(String email,String resetLink) { client.sendText(email,ResetEmailContent.SUBJECT,ResetEmailContent.text(resetLink)); }
}
