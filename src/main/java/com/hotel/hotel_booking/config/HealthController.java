package com.hotel.hotel_booking.config;

import javax.sql.DataSource;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    private final DataSource data;
    public HealthController(DataSource data) {this.data=data;}
    public record Status(String status) {}

    @GetMapping("/api/health")
    public ResponseEntity<Status> health() {
        try(var connection=data.getConnection();var statement=connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(3);
            try(var result=statement.executeQuery()) {
                if(result.next()&&result.getInt(1)==1)return ResponseEntity.ok(new Status("UP"));
            }
        } catch(SQLException failure) {
            // Health responses never expose hosts, credentials, SQL errors or stack traces.
        }
        return ResponseEntity.status(503).body(new Status("DOWN"));
    }
}
