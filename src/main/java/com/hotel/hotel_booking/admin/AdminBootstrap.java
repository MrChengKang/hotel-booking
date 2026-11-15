package com.hotel.hotel_booking.admin;

import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.auth.AuthService;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit local/server configuration only. Never grants an existing guest/host admin rights. */
@Component @ConditionalOnProperty(name="app.admin.bootstrap.enabled",havingValue="true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;private final PasswordEncoder encoder;
    private final String email,name,password;
    public AdminBootstrap(UserRepository users,PasswordEncoder encoder,
        @Value("${app.admin.bootstrap.email:}") String email,@Value("${app.admin.bootstrap.name:Platform Admin}") String name,
        @Value("${app.admin.bootstrap.password:}") String password){this.users=users;this.encoder=encoder;this.email=email;this.name=name;this.password=password;}
    @Override @Transactional public void run(ApplicationArguments arguments){
        if(email.isBlank()||email.length()>254||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||name.isBlank()||name.strip().length()>80
            ||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Configure a separate admin email, name, and password (12+ characters, at most 72 UTF-8 bytes), or disable admin bootstrap.");
        var address=AuthService.normalizeEmail(email);var existing=users.findByEmail(address);
        if(existing.isPresent()){
            if(existing.get().getRole()!=Role.ADMIN)throw new IllegalStateException("Admin bootstrap refuses to promote an existing guest/host. Choose a separate email.");
            return; // Never rotates an existing admin password on subsequent starts.
        }
        users.saveAndFlush(new User(name.strip(),address,encoder.encode(password),Role.ADMIN));
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Platform admin created. Disable bootstrap and remove its plaintext password from local configuration.");
    }
}
