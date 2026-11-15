package com.hotel.hotel_booking.hotel;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;

@RestController
@RequestMapping("/api/host")
public class HostHotelController {
    private final HotelManagementService service;
    public HostHotelController(HotelManagementService service) { this.service=service; }
    @GetMapping("/hotels") public PageResponse<HotelView> list(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.ownHotels(p.getName(),page,size); }
    @PostMapping("/hotels") @ResponseStatus(HttpStatus.CREATED)
    public HotelView create(Principal p,@Valid @RequestBody HotelCreate in) { return service.create(p.getName(),in); }
    @GetMapping("/hotels/{id}") public HotelView get(Principal p,@PathVariable Long id) { return service.ownHotel(id,p.getName()); }
    @PostMapping("/hotels/{id}/submit") public HotelView submit(Principal p,@PathVariable Long id) { return service.submit(id,p.getName()); }
    @PutMapping("/hotels/{id}") public HotelView edit(Principal p,@PathVariable Long id,@Valid @RequestBody HotelEdit in) { return service.edit(id,p.getName(),in); }
    @PatchMapping("/hotels/{id}/status") public HotelView status(Principal p,@PathVariable Long id,@Valid @RequestBody StatusChange in) { return service.status(id,p.getName(),in.status()); }
    @GetMapping("/hotels/{id}/room-types") public List<RoomView> rooms(Principal p,@PathVariable Long id) { return service.ownRooms(id,p.getName()); }
    @PostMapping("/hotels/{id}/room-types") @ResponseStatus(HttpStatus.CREATED)
    public RoomView createRoom(Principal p,@PathVariable Long id,@Valid @RequestBody RoomCreate in) { return service.createRoom(id,p.getName(),in); }
    @PutMapping("/room-types/{id}") public RoomView editRoom(Principal p,@PathVariable Long id,@Valid @RequestBody RoomEdit in) { return service.editRoom(id,p.getName(),in); }
    @PostMapping("/room-types/{id}/inventory/initialize") public CreatedNights initialize(Principal p,@PathVariable Long id,@Valid @RequestBody DateRange in) { return service.initialize(id,p.getName(),in); }
    @GetMapping("/room-types/{id}/inventory") public List<NightView> nights(Principal p,@PathVariable Long id,@RequestParam LocalDate from,@RequestParam LocalDate to) { return service.nights(id,p.getName(),from,to); }
    @PutMapping("/room-types/{id}/inventory/{date}") public NightView editNight(Principal p,@PathVariable Long id,@PathVariable LocalDate date,@Valid @RequestBody NightEdit in) { return service.editNight(id,date,p.getName(),in); }
}
