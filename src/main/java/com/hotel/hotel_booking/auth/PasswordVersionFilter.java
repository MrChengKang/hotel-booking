package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordVersionFilter extends OncePerRequestFilter {
    public static final String VERSION = "STAY_PASSWORD_VERSION";
    private final UserRepository users;
    private final org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices remember;
    public PasswordVersionFilter(UserRepository users,org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices remember) { this.users = users; this.remember=remember; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/auth/csrf") || path.equals("/api/auth/login") || path.equals("/api/auth/register")
            || path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            var session = request.getSession(false);
            var user = users.findByEmail(auth.getName());
            if (session == null || user.isEmpty() || !Long.valueOf(user.get().getPasswordVersion()).equals(session.getAttribute(VERSION))) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                remember.loginFail(request,response);
                response.setStatus(401); response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your session expired. Please sign in again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
