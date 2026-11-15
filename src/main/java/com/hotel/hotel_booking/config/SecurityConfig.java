package com.hotel.hotel_booking.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.security.web.authentication.rememberme.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import com.hotel.hotel_booking.auth.AuthSessionStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "X-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
    @Bean TokenBasedRememberMeServices rememberMeServices(UserDetailsService users,
            @Value("${app.auth.remember-key:}") String configuredKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
        // Local key is configured separately; a missing deployment key is ephemeral per boot.
        String key = configuredKey.isBlank() ? java.util.UUID.randomUUID().toString()+java.util.UUID.randomUUID() : configuredKey;
        if (key.length()<32) throw new IllegalArgumentException("Remember-me signing key must contain at least 32 characters.");
        var remember = new TokenBasedRememberMeServices(key, users, TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256);
        remember.setCookieName("STAY_REMEMBER_ME");
        remember.setAlwaysRemember(true); // Only the JSON controller's explicit true branch issues it.
        remember.setTokenValiditySeconds(30*24*60*60);
        if (secure) remember.setUseSecureCookie(true);
        String cookieSite=switch(sameSite.toLowerCase(java.util.Locale.ROOT)) {
            case "lax" -> "Lax";
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> throw new IllegalArgumentException("Cookie SameSite must be lax, strict or none.");
        };
        if(cookieSite.equals("None")&&!secure)throw new IllegalArgumentException("SameSite=None requires secure cookies.");
        remember.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", cookieSite));
        return remember;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrf, com.hotel.hotel_booking.user.UserRepository users,
            TokenBasedRememberMeServices remember, AuthSessionStrategy sessions) throws Exception {
        var remembered = new RememberMeAuthenticationFilter(
            new ProviderManager(new RememberMeAuthenticationProvider(remember.getKey())), remember);
        remembered.setSecurityContextRepository(contexts);
        remembered.setSessionAuthenticationStrategy(sessions);
        return http
            .cors(cors -> {})
            .addFilterAfter(new com.hotel.hotel_booking.auth.PasswordVersionFilter(users,remember), org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterBefore(remembered, AnonymousAuthenticationFilter.class)
            .csrf(config -> config.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/payments/webhook").csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(config -> config.securityContextRepository(contexts))
            .requestCache(config -> config.disable())
            .formLogin(config -> config.disable())
            .httpBasic(config -> config.disable())
            .logout(config -> config.logoutUrl("/api/auth/logout")
                .addLogoutHandler(remember)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .authorizeHttpRequests(config -> config
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/payments/config", "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/hotels", "/api/hotels/**", "/api/media/**").permitAll()
                .requestMatchers("/api/bookings", "/api/bookings/**").hasRole("USER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/host/**").hasRole("HOST")
                .requestMatchers("/api/account/**").authenticated()
                .requestMatchers("/api/auth/me").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(config -> config
                .authenticationEntryPoint((request, response, failure) -> error(response, 401, "Please sign in."))
                .accessDeniedHandler((request, response, failure) -> error(response, 403, "Access denied or session expired. Refresh and try again.")))
            .build();
    }
    private static void error(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
