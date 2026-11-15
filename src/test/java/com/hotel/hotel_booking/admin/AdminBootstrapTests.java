package com.hotel.hotel_booking.admin;
import com.hotel.hotel_booking.user.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminBootstrapTests {
    @Test void explicitlyConfiguredAdminUsesHashedPasswordAndNormalizedEmail(){
        var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.empty());when(encoder.encode("DemoOnlyPass123")).thenReturn("hashed");
        var bootstrap=new AdminBootstrap(users,encoder,"ADMIN@example.com","Platform Admin","DemoOnlyPass123");bootstrap.run(null);
        var capture=org.mockito.ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(capture.capture());
        assertEquals(Role.ADMIN,capture.getValue().getRole());assertEquals("hashed",capture.getValue().getPasswordHash());assertEquals("admin@example.com",capture.getValue().getEmail());
    }
    @Test void existingGuestCannotBePromotedByBootstrap(){
        var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        when(users.findByEmail("guest@example.com")).thenReturn(Optional.of(new User("Guest","guest@example.com","hash",Role.USER)));
        assertThrows(IllegalStateException.class,()->new AdminBootstrap(users,encoder,"guest@example.com","Admin","DemoOnlyPass123").run(null));
        verify(users,never()).saveAndFlush(any());verifyNoInteractions(encoder);
    }
    @Test void existingAdminPasswordIsNotSilentlyReset(){
        var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(new User("Admin","admin@example.com","oldhash",Role.ADMIN)));
        new AdminBootstrap(users,encoder,"admin@example.com","Admin","DemoOnlyPass123").run(null);
        verify(users,never()).saveAndFlush(any());verifyNoInteractions(encoder);
    }
    @Test void missingOrWeakCredentialsDoNotCreateAnAccount(){
        var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        assertThrows(IllegalStateException.class,()->new AdminBootstrap(users,encoder,"","Admin","").run(null));
        assertThrows(IllegalStateException.class,()->new AdminBootstrap(users,encoder,"admin@example.com","Admin","short").run(null));
        verifyNoInteractions(users,encoder);
    }
}
