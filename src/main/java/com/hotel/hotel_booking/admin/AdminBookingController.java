package com.hotel.hotel_booking.admin;

import com.hotel.hotel_booking.hotel.HotelDtos.PageResponse;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class AdminBookingController {
    private final AdminBookingService service;
    public AdminBookingController(AdminBookingService service){this.service=service;}

    @GetMapping("/bookings")
    public PageResponse<AdminBookingService.BookingItem> bookings(Principal principal,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String search,@RequestParam(required=false) String hotel,
        @RequestParam(required=false) Long hotelId,@RequestParam(required=false) String status,
        @RequestParam(required=false) String paymentStatus,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate checkInFrom,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate checkInTo){
        return service.bookings(principal.getName(),search,hotel,hotelId,status,paymentStatus,checkInFrom,checkInTo,page,size);
    }
    @GetMapping("/bookings/{id}")
    public AdminBookingService.Detail detail(Principal principal,@PathVariable Long id){return service.detail(principal.getName(),id);}

    @GetMapping("/refunds")
    public PageResponse<AdminBookingService.RefundItem> refunds(Principal principal,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String search,@RequestParam(required=false) String hotel,
        @RequestParam(required=false) Long hotelId,@RequestParam(required=false) String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate checkInFrom,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate checkInTo){
        return service.refunds(principal.getName(),search,hotel,hotelId,status,checkInFrom,checkInTo,page,size);
    }
}
