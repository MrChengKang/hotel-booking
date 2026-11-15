package com.hotel.hotel_booking.notification;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.hotel.hotel_booking.hotel.HotelDtos;

@RestController @RequestMapping("/api/account/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public HotelDtos.PageResponse<NotificationService.View> list(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return service.list(p.getName(),page,size);}
    @GetMapping("/unread-count") public NotificationService.Count count(Principal p){return service.count(p.getName());}
    @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(Principal p,@PathVariable Long id){service.read(p.getName(),id);}
    @PostMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void all(Principal p){service.readAll(p.getName());}
}
