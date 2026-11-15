package com.hotel.hotel_booking.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }
    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min = 8, max = 72) String password) {}
    public record Message(String message) {}
    @PostMapping("/forgot-password")
    public Message forgot(@Valid @RequestBody ForgotRequest input) {
        service.requestReset(input.email());
        return new Message("If an account exists for this email, a reset link will be sent. Check your inbox and spam folder.");
    }
    @PostMapping("/reset-password")
    public Message reset(@Valid @RequestBody ResetRequest input) {
        service.reset(input.token(), input.password());
        return new Message("Your password has been reset. Please sign in with your new password.");
    }
}
