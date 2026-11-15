package com.hotel.hotel_booking;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthApiTests {
    @LocalServerPort int port;
    @Test void anonymousHealthChecksUseTheDatabaseWithoutCreatingSessions() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());assertEquals("{\"status\":\"UP\"}",response.body());
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
    }
}
