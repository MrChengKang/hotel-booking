package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.user.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AuthService(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }

    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    @Transactional
    public User register(AuthDtos.RegisterRequest input) {
        String email = normalizeEmail(input.email());
        Role role;
        try { role = Role.valueOf(input.role().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose User or Hotel Host."); }
        if (role == Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin registration is unavailable.");
        // BCrypt limits input by UTF-8 bytes, not Java character count.
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        return users.saveAndFlush(new User(input.name().strip(), email, encoder.encode(input.password()), role));
    }

    @Transactional(readOnly = true)
    public User findUser(String email) {
        return users.findByEmail(normalizeEmail(email)).orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    public static class AccountPrincipal extends org.springframework.security.core.userdetails.User {
        private final long passwordVersion;
        public AccountPrincipal(User user) {
            super(user.getEmail(), user.getPasswordHash(),
                org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_" + user.getRole().name()));
            this.passwordVersion = user.getPasswordVersion();
        }
        public long getPasswordVersion() { return passwordVersion; }
    }
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = findUser(email);
        return new AccountPrincipal(user);
    }
}
