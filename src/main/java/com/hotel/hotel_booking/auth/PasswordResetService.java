package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetRepository tokens;
    private final PasswordEncoder encoder;
    private final ResetMailSender mail;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();
    private final String frontend;
    public PasswordResetService(UserRepository users, PasswordResetRepository tokens, PasswordEncoder encoder,
        ResetMailSender mail, ApplicationEventPublisher events, @Value("${app.frontend-url}") String frontend) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.mail = mail; this.events = events;
        this.frontend = frontend.replaceAll("/+$", "");
    }
    public record ResetEmail(String email, String link) {}

    @Transactional
    public void requestReset(String email) {
        // Check server configuration independently of whether this account exists.
        if (!mail.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Password reset email is not configured yet. Please contact support.");
        var account = users.findByEmail(AuthService.normalizeEmail(email));
        if (account.isEmpty()) return;
        var user = users.findLockedById(account.get().getId()).orElseThrow();
        Instant now = Instant.now();
        var previous = tokens.findById(user.getId());
        if (previous.isPresent() && previous.get().getIssuedAt().plusSeconds(60).isAfter(now)) return;
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.saveAndFlush(new PasswordResetToken(user.getId(), digest(raw), now, now.plusSeconds(900)));
        // Fragment tokens are not sent in HTTP requests or Referer headers.
        events.publishEvent(new ResetEmail(user.getEmail(), frontend + "/reset-password#token=" + raw));
    }

    @TransactionalEventListener
    public void deliver(ResetEmail event) {
        try { mail.send(event.email(), event.link()); }
        catch (RuntimeException failure) {
            // Keep the same public response for existing and unknown addresses; never log the link or credentials.
            if (failure instanceof com.hotel.hotel_booking.mail.MailDeliveryException mailFailure)
                LoggerFactory.getLogger(PasswordResetService.class).error("STAY reset email delivery failed (Brevo HTTP {}). Check mail provider configuration.",mailFailure.httpStatus());
            else LoggerFactory.getLogger(PasswordResetService.class).error("STAY reset email delivery failed. Check mail provider configuration.");
        }
    }

    @Transactional
    public void reset(String raw, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        String hash = digest(raw);
        Long userId = tokens.findUserIdByHash(hash).orElseThrow(PasswordResetService::invalid);
        var user = users.findLockedById(userId).orElseThrow(PasswordResetService::invalid);
        // All issue/consume operations lock the user first, so concurrent resets cannot consume a link twice.
        var token = tokens.findByTokenHash(hash).orElseThrow(PasswordResetService::invalid);
        if (!token.getExpiresAt().isAfter(Instant.now())) throw invalid();
        user.changePassword(encoder.encode(password));
        tokens.delete(token);
        tokens.flush();
    }
    public static String digest(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or expired. Request a new link.");
    }
}
