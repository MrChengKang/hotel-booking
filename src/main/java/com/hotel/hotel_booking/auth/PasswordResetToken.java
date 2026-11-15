package com.hotel.hotel_booking.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stay_password_reset_tokens")
public class PasswordResetToken {
    @Id private Long userId;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    protected PasswordResetToken() {}
    public PasswordResetToken(Long userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId; this.tokenHash = tokenHash; this.issuedAt = issuedAt; this.expiresAt = expiresAt;
    }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
