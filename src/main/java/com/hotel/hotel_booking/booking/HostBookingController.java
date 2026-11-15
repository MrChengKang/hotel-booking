package com.hotel.hotel_booking.booking;

import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import com.hotel.hotel_booking.hotel.HotelDtos.PageResponse;
import com.hotel.hotel_booking.booking.BookingDtos.HostBooking;

@RestController
@RequestMapping("/api/host/hotels/{hotelId}/bookings")
public class HostBookingController {
    private final BookingService service;
    public HostBookingController(BookingService service){this.service=service;}
    @GetMapping public PageResponse<HostBooking> list(Principal p,@PathVariable Long hotelId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.hostList(p.getName(),hotelId,page,size);}
}
