package com.hotel.hotel_booking.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class GmailResetMailSender implements ResetMailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final String username;
    private final String password;
    public GmailResetMailSender(ObjectProvider<JavaMailSender> sender,
        @Value("${spring.mail.username:}") String username, @Value("${spring.mail.password:}") String password) {
        this.sender = sender; this.username = username; this.password = password;
    }
    public boolean available() { return !username.isBlank() && !password.isBlank() && sender.getIfAvailable() != null; }
    public void send(String email, String resetLink) {
        var mail = new SimpleMailMessage();
        mail.setFrom(username);
        mail.setTo(email);
        mail.setSubject(ResetEmailContent.SUBJECT);
        mail.setText(ResetEmailContent.text(resetLink));
        sender.getObject().send(mail);
    }
}
