package com.hotel.hotel_booking.booking;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.hotel.hotel_booking.hotel.HotelDtos.PageResponse;
import static com.hotel.hotel_booking.booking.BookingDtos.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService service;
    private final com.hotel.hotel_booking.payment.PaymentService payments;
    public BookingController(BookingService service,com.hotel.hotel_booking.payment.PaymentService payments){this.service=service;this.payments=payments;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Detail create(Principal p,@Valid @RequestBody Create in){return service.create(p.getName(),in);}
    @GetMapping public PageResponse<Summary> list(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(p.getName(),page,size);}
    @GetMapping("/{id}") public Detail get(Principal p,@PathVariable Long id){return service.get(p.getName(),id);}
    @PostMapping("/{id}/cancel") public Detail cancel(Principal p,@PathVariable Long id){var pending=payments.cancelPending(p.getName(),id);return pending.isPresent()?pending.get():service.cancel(p.getName(),id);}
}
