package com.hotel.hotel_booking.hotel;

import com.hotel.hotel_booking.booking.BookingRepository;
import com.hotel.hotel_booking.booking.BookingStatus;
import com.hotel.hotel_booking.user.Role;
import java.security.Principal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/host/overview")
public class HostOverviewController {
    private final HotelManagementService management;
    private final HotelRepository hotels;
    private final BookingRepository bookings;
    public HostOverviewController(HotelManagementService management, HotelRepository hotels, BookingRepository bookings) {
        this.management=management; this.hotels=hotels; this.bookings=bookings;
    }
    public record Overview(long hotels,long publishedHotels,long bookings,long confirmedBookings) {}
    @GetMapping @Transactional(readOnly=true)
    public Overview overview(Principal principal) {
        Long owner=management.actor(principal.getName(),Role.HOST).getId();
        return new Overview(hotels.countByHostId(owner), hotels.countByHostIdAndStatus(owner,HotelStatus.PUBLISHED),
            bookings.countByRoomTypeHotelHostId(owner),bookings.countByRoomTypeHotelHostIdAndStatus(owner,BookingStatus.CONFIRMED));
    }
}
