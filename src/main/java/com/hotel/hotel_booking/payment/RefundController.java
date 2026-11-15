package com.hotel.hotel_booking.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings/{id}")
public class RefundController {
    private final RefundService refunds;
    public RefundController(RefundService refunds){this.refunds=refunds;}
    public record Request(@NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal expectedAmount) {}
    @GetMapping("/cancellation") public RefundTransactions.View policy(Principal p,@PathVariable Long id){return refunds.view(p.getName(),id);}
    @PostMapping("/refund") @ResponseStatus(HttpStatus.ACCEPTED)
    public RefundTransactions.View request(Principal p,@PathVariable Long id,@Valid @RequestBody Request input){return refunds.request(p.getName(),id,input.expectedAmount());}
}
