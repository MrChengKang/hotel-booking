package com.hotel.hotel_booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentCookieTests {
    @Test void explicitlyConfiguredCrossSiteRememberCookieIsSecureAndHttpOnly() {
        var remember=new SecurityConfig().rememberMeServices(mock(UserDetailsService.class),"unit-test-only-stable-key-at-least-32-characters",true,"none");
        var user=User.withUsername("test@example.com").password("test-password-hash").roles("USER").build();
        var response=new MockHttpServletResponse();
        remember.loginSuccess(new MockHttpServletRequest(),response,new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
        var cookie=response.getCookie("STAY_REMEMBER_ME");
        assertNotNull(cookie);assertTrue(cookie.getSecure());assertTrue(cookie.isHttpOnly());assertEquals("None",cookie.getAttribute("SameSite"));
    }
    @Test void refusesCrossSiteRememberCookiesWithoutSecure() {
        assertThrows(IllegalArgumentException.class,()->new SecurityConfig().rememberMeServices(mock(UserDetailsService.class),"unit-test-only-stable-key-at-least-32-characters",false,"none"));
    }
}
