package com.hotel.hotel_booking.admin;

import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import com.hotel.hotel_booking.hotel.HotelDtos.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service){this.service=service;}
    public record Decision(@NotBlank @Pattern(regexp="APPROVE|REJECT") String decision,@NotNull @Min(0) Long expectedVersion,@Size(max=1000) String note) {}
    @GetMapping("/overview") public AdminService.Overview overview(Principal p){return service.overview(p.getName());}
    @GetMapping("/users") public PageResponse<AdminService.UserView> users(Principal p,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.users(p.getName(),search,page,size);}
    @GetMapping("/hotels") public PageResponse<AdminService.HotelItem> hotels(Principal p,@RequestParam(required=false) String reviewStatus,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.hotels(p.getName(),reviewStatus,search,page,size);}
    @GetMapping("/hotels/{id}") public AdminService.Detail hotel(Principal p,@PathVariable Long id){return service.detail(p.getName(),id);}
    @PostMapping("/hotels/{id}/decision") public HotelView decide(Principal p,@PathVariable Long id,@Valid @RequestBody Decision input){return service.decide(p.getName(),id,input);}
}
