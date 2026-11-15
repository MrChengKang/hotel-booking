package com.hotel.hotel_booking;

import com.hotel.hotel_booking.auth.*;
import com.hotel.hotel_booking.user.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.*;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:remember;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.auth.remember-key=only-for-h2-tests-a-long-independent-secret-key",
    "app.payments.enabled=false","app.admin.bootstrap.enabled=false"})
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RememberMeIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordResetRepository resets;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired TokenBasedRememberMeServices remember;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean ResetMailSender mail;
    String hash;
    @BeforeAll void password(){hash=encoder.encode("SampleOnly123");}
    @BeforeEach void prepare(){
        resets.deleteAll();users.deleteAll();
        users.save(new User("Remembered Guest","remember@example.com",hash,Role.USER));
        users.save(new User("Remembered Host","host@example.com",hash,Role.HOST));
        users.save(new User("Remembered Admin","admin@example.com",hash,Role.ADMIN));
        org.mockito.Mockito.reset(mail);org.mockito.Mockito.when(mail.available()).thenReturn(true);
    }
    String origin(){return "http://localhost:"+port;}
    class Browser {
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        Browser(){}
        Browser(String value){var cookie=new HttpCookie("STAY_REMEMBER_ME",value);cookie.setPath("/");cookie.setDomain("localhost.local");cookie.setVersion(0);cookies.getCookieStore().add(URI.create(origin()),cookie);}
        HttpResponse<String> send(String method,String path,String body,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create(origin()+path));
            if(csrf)request.header("X-CSRF-TOKEN",json.readTree(send("GET","/api/auth/csrf",null,false).body()).path("token").asText());
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> me()throws Exception{return send("GET","/api/auth/me",null,false);}
        HttpResponse<String> login(String email,boolean persistent)throws Exception{
            return send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\",\"rememberMe\":"+persistent+"}",true);
        }
        String token(){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("STAY_REMEMBER_ME")&&!c.hasExpired()).map(HttpCookie::getValue).findFirst().orElse(null);}
    }
    @Test void explicitOptInSetsPrivatePersistentCookieAndRestoresSession()throws Exception{
        var browser=new Browser();var login=browser.login("remember@example.com",true);
        assertEquals(200,login.statusCode(),login.body());
        String header=login.headers().allValues("set-cookie").stream().filter(s->s.startsWith("STAY_REMEMBER_ME=")).findFirst().orElseThrow();
        assertTrue(header.toLowerCase().contains("httponly"));assertTrue(header.toLowerCase().contains("samesite=lax"));
        assertTrue(header.contains("Max-Age=2592000"));
        String raw=new String(Base64.getDecoder().decode(browser.token()),StandardCharsets.UTF_8);
        assertFalse(raw.contains("SampleOnly123"));assertFalse(raw.contains(hash));
        var reopened=new Browser(browser.token()); // Copy only persistent cookie; session cookie is gone.
        assertEquals(200,reopened.me().statusCode());
        assertEquals(200,reopened.me().statusCode()); // Newly saved context also has password version.
        assertEquals(204,reopened.send("POST","/api/auth/logout",null,true).statusCode()); // Fresh CSRF after restoration works.
        assertNull(reopened.token());assertEquals(401,reopened.me().statusCode());
    }
    @Test void uncheckedLoginClearsPreviousPersistenceAndOldClientsRemainSessionOnly()throws Exception{
        var browser=new Browser();assertEquals(200,browser.login("remember@example.com",true).statusCode());
        assertNotNull(browser.token());assertEquals(200,browser.login("remember@example.com",false).statusCode());
        assertNull(browser.token());assertEquals(200,browser.me().statusCode());assertEquals(401,new Browser().me().statusCode());
        var oldClient=new Browser();
        assertEquals(200,oldClient.send("POST","/api/auth/login","{\"email\":\"remember@example.com\",\"password\":\"SampleOnly123\"}",true).statusCode());
        assertNull(oldClient.token());
    }
    @Test void allRolesRestoreAndReloadCurrentDatabaseAuthorities()throws Exception{
        for(String role:List.of("remember","host","admin")){
            var browser=new Browser();assertEquals(200,browser.login(role+"@example.com",true).statusCode());
            var reopened=new Browser(browser.token());var me=reopened.me();assertEquals(200,me.statusCode(),me.body());
            assertTrue(me.body().contains("\"role\":\""+(role.equals("remember")?"user":role)+"\""));
            if(role.equals("host")){
                jdbc.update("update stay_users set role='USER' where email='host@example.com'");
                var changed=new Browser(browser.token());assertTrue(changed.me().body().contains("\"role\":\"user\""));
                assertEquals(401,changed.send("GET","/api/host/overview",null,false).statusCode()); // Remembered wrong-role access requires fresh sign-in.
            }
        }
    }
    @Test void failedLoginAndMissingCsrfNeverIssuePersistentCredentials()throws Exception{
        var browser=new Browser();
        assertEquals(403,browser.send("POST","/api/auth/login","{\"email\":\"remember@example.com\",\"password\":\"SampleOnly123\",\"rememberMe\":true}",false).statusCode());
        assertEquals(401,browser.send("POST","/api/auth/login","{\"email\":\"remember@example.com\",\"password\":\"WrongPassword\",\"rememberMe\":true}",true).statusCode());
        assertNull(browser.token());
    }
    static class CookieSigner extends TokenBasedRememberMeServices {
        CookieSigner(String key,AuthService users){super(key,users);}
        String sign(long expiry,String email,String password){return encodeCookie(new String[]{email,Long.toString(expiry),"SHA256",makeTokenSignature(expiry,email,password,RememberMeTokenAlgorithm.SHA256)});}
    }
    @Test void expiredTamperedAndDifferentKeyTokensAreRejected()throws Exception{
        var browser=new Browser();browser.login("remember@example.com",true);
        String value=browser.token();String decoded=new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);
        String bad=Base64.getEncoder().encodeToString((decoded.substring(0,decoded.length()-1)+(decoded.endsWith("a")?"b":"a")).getBytes(StandardCharsets.UTF_8));
        var signer=new CookieSigner(remember.getKey(),auth);
        for(String token:List.of(bad,signer.sign(System.currentTimeMillis()-1000,"remember@example.com",hash),
            new CookieSigner("different-private-key-with-32-characters",auth).sign(System.currentTimeMillis()+60000,"remember@example.com",hash))){
            var reopened=new Browser(token);assertEquals(401,reopened.me().statusCode());assertNull(reopened.token());
        }
    }
    @Test void passwordResetInvalidatesRememberedCookieAndRestoredSessions()throws Exception{
        var browser=new Browser();browser.login("remember@example.com",true);String old=browser.token();
        var restored=new Browser(old);assertEquals(200,restored.me().statusCode());
        assertEquals(200,browser.send("POST","/api/auth/forgot-password","{\"email\":\"remember@example.com\"}",true).statusCode());
        var link=org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.eq("remember@example.com"),link.capture());
        String token=link.getValue().split("#token=")[1];
        assertEquals(200,browser.send("POST","/api/auth/reset-password","{\"token\":\""+token+"\",\"password\":\"NewSample123\"}",true).statusCode());
        assertEquals(401,new Browser(old).me().statusCode());assertEquals(401,restored.me().statusCode());assertNull(restored.token());
    }
    @Test void secureRequestsReceiveSecureCookie()throws Exception{
        var request=new MockHttpServletRequest();request.setSecure(true);var response=new MockHttpServletResponse();
        var principal=auth.loadUserByUsername("remember@example.com");
        remember.loginSuccess(request,response,org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        var cookie=response.getCookie("STAY_REMEMBER_ME");assertNotNull(cookie);assertTrue(cookie.getSecure());assertTrue(cookie.isHttpOnly());
    }
}
