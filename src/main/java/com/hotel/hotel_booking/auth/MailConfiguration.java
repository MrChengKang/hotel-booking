package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.mail.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class MailConfiguration {
    @Bean BrevoMailClient brevoMailClient(Environment env,ObjectMapper json) {
        return new BrevoMailClient(new BrevoMailSettings(env),json);
    }
    @Bean ResetMailSender resetMailSender(Environment env,ObjectProvider<JavaMailSender> smtp,BrevoMailClient brevo) {
        return switch (env.getProperty("app.mail.provider","smtp").strip()) {
            case "smtp" -> new GmailResetMailSender(smtp,env.getProperty("spring.mail.username",""),env.getProperty("spring.mail.password",""));
            case "brevo" -> new BrevoResetMailSender(brevo);
            default -> throw new IllegalStateException("STAY_MAIL_PROVIDER must be smtp or brevo.");
        };
    }
}
