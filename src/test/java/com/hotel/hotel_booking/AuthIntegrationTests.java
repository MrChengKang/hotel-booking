package com.hotel.hotel_booking;

import com.hotel.hotel_booking.user.UserRepository;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.net.CookieManager;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired com.hotel.hotel_booking.auth.PasswordResetRepository resets;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.hotel.hotel_booking.auth.ResetMailSender mail;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    HttpClient client;
    String csrf;

    @BeforeEach void prepare() throws Exception {
        resets.deleteAll();
        users.deleteAll();
        org.mockito.Mockito.when(mail.available()).thenReturn(true);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        refreshCsrf();
    }
    HttpResponse<String> request(String method, String path, String body, boolean token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    void refreshCsrf() throws Exception {
        var response = request("GET", "/api/auth/csrf", null, false);
        assertEquals(200, response.statusCode());
        var matcher = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(response.body());
        assertTrue(matcher.find());
        csrf = matcher.group(1);
    }
    String registration(String role) {
        return "{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"SampleOnly123\",\"role\":\"" + role + "\"}";
    }
    @Test void registrationSessionLoginLogoutAndPasswordStorage() throws Exception {
        assertEquals(401, request("GET", "/api/auth/me", null, false).statusCode());
        var created = request("POST", "/api/auth/register", registration("host"), true);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains("\"role\":\"host\""));
        assertFalse(created.body().contains("password"));
        var user = users.findByEmail("alex@example.com").orElseThrow();
        assertTrue(encoder.matches("SampleOnly123", user.getPasswordHash()));
        assertEquals(200, request("GET", "/api/auth/me", null, false).statusCode());
        refreshCsrf();
        assertEquals(204, request("POST", "/api/auth/logout", null, true).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, false).statusCode());
        refreshCsrf();
        assertEquals(401, request("POST", "/api/auth/login", "{\"email\":\"alex@example.com\",\"password\":\"WrongPassword\"}", true).statusCode());
        assertEquals(200, request("POST", "/api/auth/login", "{\"email\":\"ALEX@example.com\",\"password\":\"SampleOnly123\"}", true).statusCode());
        assertEquals(200, request("GET", "/api/auth/me", null, false).statusCode());
        assertEquals(403, request("GET", "/api/admin/users", null, false).statusCode());
    }
    @Test void rejectsAdminDuplicateInvalidInputAndMissingCsrf() throws Exception {
        assertEquals(403, request("POST", "/api/auth/register", registration("user"), false).statusCode());
        assertEquals(400, request("POST", "/api/auth/register", registration("admin"), true).statusCode());
        assertEquals(201, request("POST", "/api/auth/register", registration("user"), true).statusCode());
        refreshCsrf();
        assertEquals(409, request("POST", "/api/auth/register", registration("user"), true).statusCode());
        assertEquals(400, request("POST", "/api/auth/register", registration("user").replace("SampleOnly123", "short"), true).statusCode());
        assertEquals(1, users.count());
    }
    @Test void legacyUsersTableDoesNotBlockNewStayRegistration() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY, email VARCHAR(254))");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", 1, "alex@example.com");
        var response = request("POST", "/api/auth/register", registration("user"), true);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(1, users.count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
    }
    String forgotBody(String email) { return "{\"email\":\"" + email + "\"}"; }
    String resetBody(String token, String password) { return "{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"; }
    @Test void resetIsPrivateSingleUseAndInvalidatesOldSessions() throws Exception {
        assertEquals(201, request("POST", "/api/auth/register", registration("user"), true).statusCode());
        refreshCsrf();
        var known = request("POST", "/api/auth/forgot-password", forgotBody("alex@example.com"), true);
        var unknown = request("POST", "/api/auth/forgot-password", forgotBody("unknown@example.com"), true);
        assertEquals(200, known.statusCode());
        assertEquals(known.body(), unknown.body());
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.eq("alex@example.com"), link.capture());
        String token = link.getValue().split("#token=")[1];
        var record = resets.findAll().getFirst();
        assertNotEquals(token, record.getTokenHash());
        assertEquals(64, record.getTokenHash().length());
        assertEquals(900, java.time.Duration.between(record.getIssuedAt(), record.getExpiresAt()).getSeconds());
        // Repeated requests within a minute keep the original valid link and send no additional email.
        request("POST", "/api/auth/forgot-password", forgotBody("alex@example.com"), true);
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertEquals(200, request("POST", "/api/auth/reset-password", resetBody(token, "NewSample123"), true).statusCode());
        assertEquals(401, request("GET", "/api/auth/me", null, false).statusCode());
        refreshCsrf();
        assertEquals(400, request("POST", "/api/auth/reset-password", resetBody(token, "AnotherSample123"), true).statusCode());
        assertEquals(401, request("POST", "/api/auth/login", "{\"email\":\"alex@example.com\",\"password\":\"SampleOnly123\"}", true).statusCode());
        assertEquals(200, request("POST", "/api/auth/login", "{\"email\":\"alex@example.com\",\"password\":\"NewSample123\"}", true).statusCode());
    }
    @Test void rejectsExpiredTokensAndUnconfiguredEmail() throws Exception {
        org.mockito.Mockito.when(mail.available()).thenReturn(false);
        assertEquals(503, request("POST", "/api/auth/forgot-password", forgotBody("unknown@example.com"), true).statusCode());
        org.mockito.Mockito.when(mail.available()).thenReturn(true);
        assertEquals(201, request("POST", "/api/auth/register", registration("user"), true).statusCode());
        refreshCsrf();
        String raw = "a".repeat(43);
        var now = java.time.Instant.now();
        resets.save(new com.hotel.hotel_booking.auth.PasswordResetToken(users.findAll().getFirst().getId(),
            com.hotel.hotel_booking.auth.PasswordResetService.digest(raw), now.minusSeconds(1000), now.minusSeconds(1)));
        assertEquals(400, request("POST", "/api/auth/reset-password", resetBody(raw, "NewSample123"), true).statusCode());
        assertTrue(encoder.matches("SampleOnly123", users.findAll().getFirst().getPasswordHash()));
        assertEquals(403, request("POST", "/api/auth/reset-password", resetBody(raw, "NewSample123"), false).statusCode());
    }
    @Test void corsOnlyAllowsConfiguredOrigin() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
            .header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "GET")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void mailDeliveryFailureDoesNotExposeAccountStatusOrProviderDetails(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        assertEquals(201,request("POST","/api/auth/register",registration("user"),true).statusCode());
        refreshCsrf();
        org.mockito.Mockito.doThrow(new IllegalStateException("private-provider-detail-do-not-log"))
            .when(mail).send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        var known=request("POST","/api/auth/forgot-password",forgotBody("alex@example.com"),true);
        var unknown=request("POST","/api/auth/forgot-password",forgotBody("unknown@example.com"),true);
        assertEquals(200,known.statusCode());assertEquals(known.body(),unknown.body());
        assertFalse(known.body().contains("token="));assertEquals(1,resets.count());
        assertFalse(output.getAll().contains("private-provider-detail-do-not-log"));
    }
}
