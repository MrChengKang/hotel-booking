package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.user.User;
import jakarta.validation.constraints.*;
import java.util.Locale;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Pattern(regexp = "(?i)user|host", message = "must be user or host") String role
    ) {}
    public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password,
        Boolean rememberMe
    ) {}
    public record UserResponse(Long id, String name, String email, String role, String avatarUrl) {
        public static UserResponse from(User user) { return from(user,null); }
        public static UserResponse from(User user,String avatarUrl) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name().toLowerCase(Locale.ROOT),avatarUrl);
        }
    }
    public record CsrfResponse(String headerName, String token) {}
    public record ApiError(String message) {}
}
