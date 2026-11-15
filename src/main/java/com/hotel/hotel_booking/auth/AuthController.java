package com.hotel.hotel_booking.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.bind.annotation.*;
import static com.hotel.hotel_booking.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final com.hotel.hotel_booking.account.AvatarService avatars;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final AuthSessionStrategy sessions;
    private final TokenBasedRememberMeServices remember;
    public AuthController(AuthService service, AuthenticationManager authenticationManager,
            SecurityContextRepository contexts, AuthSessionStrategy sessions, TokenBasedRememberMeServices remember, com.hotel.hotel_booking.account.AvatarService avatars) {
        this.service = service; this.authenticationManager = authenticationManager; this.contexts = contexts; this.sessions = sessions; this.remember = remember;this.avatars=avatars;
    }
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        var user = service.register(input);
        var principal = service.loadUserByUsername(user.getEmail());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        startSession(authentication, request, response);
        remember.logout(request,response,authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(user));
    }
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BadCredentialsException("Invalid credentials");
        var token = UsernamePasswordAuthenticationToken.unauthenticated(AuthService.normalizeEmail(input.email()), input.password());
        Authentication authentication;
        try { authentication = authenticationManager.authenticate(token); }
        catch (AuthenticationException failure) { remember.loginFail(request,response); throw failure; }
        startSession(authentication, request, response);
        if (Boolean.TRUE.equals(input.rememberMe())) remember.loginSuccess(request,response,authentication);
        else remember.logout(request,response,authentication);
        return view(service.findUser(authentication.getName()));
    }
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) { return view(service.findUser(authentication.getName())); }

    private UserResponse view(com.hotel.hotel_booking.user.User user){return UserResponse.from(user,avatars.url(user.getId()));}

    private void startSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessions.onAuthentication(authentication,request,response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);

    }
}
