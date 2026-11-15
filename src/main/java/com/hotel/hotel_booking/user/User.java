package com.hotel.hotel_booking.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stay_users", uniqueConstraints = @UniqueConstraint(name = "uk_stay_users_email", columnNames = "email"))
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Role role;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long passwordVersion = 0;

    protected User() {}
    public User(String name, String email, String passwordHash, Role role) {
        this.name = name; this.email = email; this.passwordHash = passwordHash; this.role = role;
        this.createdAt = Instant.now();
    }
    public long getPasswordVersion() { return passwordVersion; }
    public void changePassword(String hash) { this.passwordHash = hash; this.passwordVersion++; }
    public void changeName(String name) { if(name==null || name.isBlank() || name.length()>80) throw new IllegalArgumentException(); this.name=name; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
