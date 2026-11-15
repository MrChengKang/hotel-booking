package com.hotel.hotel_booking.auth;

final class ResetEmailContent {
    private ResetEmailContent() {}
    static final String SUBJECT="Reset your STAY password";
    static String text(String resetLink) {
        return "We received a request to reset your STAY password.\n\n" + resetLink
                + "\n\nThis link expires in 15 minutes and can be used only once."
                + "\nIf you did not request this, ignore this email. Your password has not changed.";
    }
}
