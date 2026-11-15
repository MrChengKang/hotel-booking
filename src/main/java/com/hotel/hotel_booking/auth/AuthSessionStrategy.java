package com.hotel.hotel_booking.auth;

import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Component;

/** The same session fixation/version/CSRF rules apply to passwords and remembered logins. */
@Component
public class AuthSessionStrategy implements SessionAuthenticationStrategy {
    private final CsrfAuthenticationStrategy csrf;
    public AuthSessionStrategy(CsrfTokenRepository repository) {
        csrf = new CsrfAuthenticationStrategy(repository);
        csrf.setRequestHandler(new CsrfTokenRequestAttributeHandler());
    }
    @Override public void onAuthentication(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var principal = (AuthService.AccountPrincipal) authentication.getPrincipal();
        var session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(PasswordVersionFilter.VERSION, principal.getPasswordVersion());
        csrf.onAuthentication(authentication, request, response);
    }
}
