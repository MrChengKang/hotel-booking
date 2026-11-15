package com.hotel.hotel_booking;

import com.hotel.hotel_booking.hotel.HotelSearchService;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:apierrors;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ApiFailureResponseTests {
    @LocalServerPort int port;
    @MockitoBean HotelSearchService search;

    @Test void publicSearchFailuresReturnSafe500InsteadOfMisleading401Or403() throws Exception {
        when(search.search(any(),any(),any(),anyInt(),anyInt(),anyInt(),any(),any(),any(),any(),any(),anyInt(),anyInt()))
            .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("test", "select secret_column from internal_table", new java.sql.SQLException("Internal database detail")));
        var response=get("/api/hotels?city=Kuala%20Lumpur");
        assertEquals(500,response.statusCode());
        assertTrue(response.body().contains("The server could not complete this request."));
        assertFalse(response.body().contains("secret_column"));assertFalse(response.body().contains("Internal database detail"));
    }
    @Test void privateEndpointsStillRequireAuthentication() throws Exception {
        assertEquals(401,get("/api/account/profile").statusCode());
        assertEquals(401,get("/api/host/hotels").statusCode());
        assertEquals(401,get("/api/admin/hotels").statusCode());
    }
    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
