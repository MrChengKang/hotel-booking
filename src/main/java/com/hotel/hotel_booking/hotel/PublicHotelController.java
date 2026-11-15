package com.hotel.hotel_booking.hotel;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;

@RestController
@RequestMapping("/api/hotels")
public class PublicHotelController {
    private final HotelManagementService service;
    private final HotelSearchService search;
    public PublicHotelController(HotelManagementService service, HotelSearchService search) { this.service=service; this.search=search; }
    @GetMapping public PageResponse<HotelView> list(@RequestParam(required=false) String city,
        @RequestParam(required=false) LocalDate checkIn,@RequestParam(required=false) LocalDate checkOut,
        @RequestParam(defaultValue="1") int rooms,@RequestParam(defaultValue="2") int guests,
        @RequestParam(defaultValue="0") int minBeds,@RequestParam(required=false) java.math.BigDecimal minPrice,
        @RequestParam(required=false) java.math.BigDecimal maxPrice,@RequestParam(required=false) String currency,
        @RequestParam(defaultValue="newest") String sort,@RequestParam(required=false) List<HotelAmenity> amenities,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return search.search(city,checkIn,checkOut,rooms,guests,minBeds,minPrice,maxPrice,currency,sort,amenities,page,size);
    }
    @GetMapping("/destinations") public List<HotelManagementService.Destination> destinations() { return service.destinations(); }
    @GetMapping("/{id}") public HotelView get(@PathVariable Long id) { return service.publicHotel(id); }
    @GetMapping("/{id}/room-types") public List<RoomView> rooms(@PathVariable Long id) { return service.publicRooms(id); }
    @GetMapping("/{id}/availability") public List<Availability> availability(@PathVariable Long id,@RequestParam LocalDate checkIn,@RequestParam LocalDate checkOut,@RequestParam(defaultValue="1") int rooms,@RequestParam(defaultValue="2") int guests) { return service.availability(id,checkIn,checkOut,rooms,guests); }
}
