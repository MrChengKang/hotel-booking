package com.hotel.hotel_booking.account;
import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.hotel.hotel_booking.account.AccountService.*;

@RestController @RequestMapping("/api/account")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service){this.service=service;}
    @GetMapping("/profile") public Profile profile(Principal p){return service.profile(p.getName());}
    @PutMapping("/profile") public Profile edit(Principal p,@Valid @RequestBody ProfileEdit in){return service.edit(p.getName(),in);}
    @GetMapping("/favorites") public List<Favorite> favorites(Principal p){return service.favorites(p.getName());}
    @PutMapping("/favorites/{hotelId}") public Favorite save(Principal p,@PathVariable Long hotelId){return service.save(p.getName(),hotelId);}
    @DeleteMapping("/favorites/{hotelId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(Principal p,@PathVariable Long hotelId){service.remove(p.getName(),hotelId);}
}
