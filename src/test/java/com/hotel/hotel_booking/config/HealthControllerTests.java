package com.hotel.hotel_booking.config;

import javax.sql.DataSource;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTests {
    @Test void databaseFailureIsServiceUnavailableWithoutPrivateDetails() throws Exception {
        var data=mock(DataSource.class);
        when(data.getConnection()).thenThrow(new SQLException("private host and password details"));
        var result=new HealthController(data).health();
        assertEquals(503,result.getStatusCode().value());assertEquals("DOWN",result.getBody().status());
        assertFalse(result.getBody().toString().contains("private"));
    }
}
