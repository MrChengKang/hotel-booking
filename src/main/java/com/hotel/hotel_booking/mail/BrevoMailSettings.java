package com.hotel.hotel_booking.mail;

import jakarta.mail.internet.InternetAddress;
import org.springframework.core.env.Environment;

/** Intentionally not a record: generated toString must never include credentials. */
public final class BrevoMailSettings {
    final String apiKey, senderEmail, senderName;

    public BrevoMailSettings(Environment env) {
        apiKey = env.getProperty("app.mail.brevo.api-key", "").strip();
        senderEmail = env.getProperty("app.mail.brevo.sender-email", "").strip();
        senderName = env.getProperty("app.mail.brevo.sender-name", "STAY").strip();
    }

    public boolean available() {
        return !apiKey.isBlank() && apiKey.length() <= 4096 && apiKey.chars().allMatch(c -> c >= 33 && c <= 126)
                && validEmail(senderEmail) && !senderName.isBlank() && senderName.length() <= 80 && !hasControls(senderName);
    }

    static boolean validEmail(String value) {
        if (value == null || value.isBlank() || value.length() > 254 || hasControls(value)) return false;
        try {
            var address = new InternetAddress(value, true);
            address.validate();
            return !address.isGroup() && value.equals(address.getAddress());
        } catch (Exception invalid) { return false; }
    }

    private static boolean hasControls(String value) { return value.chars().anyMatch(Character::isISOControl); }
}
