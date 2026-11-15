package com.hotel.hotel_booking.hotel;

import com.hotel.hotel_booking.auth.AuthService;
import com.hotel.hotel_booking.user.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;

@Service
@Transactional(readOnly=true)
public class HotelManagementService {
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final RoomInventoryRepository inventory;
    private final InventoryService stock;
    private final AuthService auth;
    private final HotelImageRepository images;
    private final com.hotel.hotel_booking.notification.NotificationService notifications;
    public HotelManagementService(HotelRepository hotels, RoomTypeRepository rooms, RoomInventoryRepository inventory, InventoryService stock, AuthService auth, HotelImageRepository images,com.hotel.hotel_booking.notification.NotificationService notifications) {
        this.hotels=hotels; this.rooms=rooms; this.inventory=inventory; this.stock=stock; this.auth=auth;this.images=images;this.notifications=notifications;
    }
    private HotelView hotelView(Hotel h){return HotelView.from(h,images.findFirstByHotelIdAndRoomTypeIsNullOrderByPositionAscIdAsc(h.getId()).map(i->"/api/media/"+i.getId()).orElse(null));}
    private HotelView publicView(Hotel h){return HotelView.publicFrom(h,images.findFirstByHotelIdAndRoomTypeIsNullOrderByPositionAscIdAsc(h.getId()).map(i->"/api/media/"+i.getId()).orElse(null));}
    private RoomView roomView(RoomType r){return RoomView.from(r,images.findFirstByRoomTypeIdOrderByPositionAscIdAsc(r.getId()).map(i->"/api/media/"+i.getId()).orElse(null));}
    public static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found."); }
    public static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
    public static Pageable page(int page,int size) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Page must be nonnegative; size must be 1–100.");
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));
    }
    public User actor(String email, Role role) {
        User user=auth.findUser(email);
        if(user.getRole()!=role) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This action is not available for your role.");
        return user;
    }
    public Hotel ownedHotel(Long id,String email,boolean lock) {
        var hotel=ownerHotel(id,email,lock);
        if(lock&&hotel.getReviewStatus().equals("PENDING"))throw conflict("Withdraw the review request before editing this hotel.");
        return hotel;
    }
    private Hotel ownerHotel(Long id,String email,boolean lock) {
        User owner=actor(email,Role.HOST);
        Hotel hotel=(lock ? hotels.findLockedById(id) : hotels.findById(id)).orElseThrow(HotelManagementService::missing);
        if(!hotel.getHost().getId().equals(owner.getId())) throw missing();
        return hotel;
    }
    public boolean administrator(String email){return auth.findUser(email).getRole()==Role.ADMIN;}
    public RoomType ownedRoom(Long id,String email,boolean lock) {
        Long hotelId=rooms.findHotelId(id).orElseThrow(HotelManagementService::missing);
        var hotel=ownedHotel(hotelId,email,lock);
        if(lock && hotel.getStatus()==HotelStatus.ARCHIVED) throw conflict("Archived hotels cannot be edited.");
        return (lock ? rooms.findLockedById(id) : rooms.findById(id)).orElseThrow(HotelManagementService::missing);
    }
    public Hotel published(Long id) {
        return hotels.findByIdAndStatus(id,HotelStatus.PUBLISHED).orElseThrow(HotelManagementService::missing);
    }
    public PageResponse<HotelView> browse(String city,int page,int size) {
        String filter=city==null || city.isBlank() ? null : city.strip();
        if(filter!=null && filter.length()>100) throw new IllegalArgumentException("City is too long.");
        return PageResponse.from(hotels.browse(filter,page(page,size)).map(this::publicView));
    }
    public record Destination(String city,long hotelCount,String coverImageUrl) {}
    public List<Destination> destinations() {
        return hotels.destinationSummary(PageRequest.of(0,12)).stream().map(row -> new Destination(
            row.getCity(),row.getHotelCount(),images.destinationCover(row.getCity(),PageRequest.of(0,1)).stream().findFirst()
                .map(image -> "/api/media/"+image.getId()).orElse(null))).toList();
    }
    public HotelView publicHotel(Long id) { return publicView(published(id)); }
    public List<RoomView> publicRooms(Long id) {
        published(id); return rooms.findByHotelIdAndActiveTrueOrderById(id).stream().map(this::roomView).toList();
    }
    public PageResponse<HotelView> ownHotels(String email,int page,int size) {
        return PageResponse.from(hotels.findByHostId(actor(email,Role.HOST).getId(),page(page,size)).map(this::hotelView));
    }
    public HotelView ownHotel(Long id,String email) { return hotelView(ownedHotel(id,email,false)); }
    @Transactional public HotelView create(String email,HotelCreate in) {
        var hotel = new Hotel(actor(email,Role.HOST),in.name(),in.slug(),in.description(),in.address(),in.city(),in.countryCode(),in.currencyCode(),in.timeZone());
        hotel.editAmenities(in.amenities()); return hotelView(hotels.save(hotel));
    }
    @Transactional public HotelView edit(Long id,String email,HotelEdit in) {
        var h=ownedHotel(id,email,true); h.edit(in.name(),in.description(),in.address(),in.city());h.editAmenities(in.amenities());hotels.flush(); return hotelView(h);
    }
    @Transactional public HotelView status(Long id,String email,HotelStatus status) {
        var h=ownerHotel(id,email,true);
        if(status==HotelStatus.ARCHIVED) h.archive();
        else if(status==HotelStatus.PUBLISHED) throw conflict("Submit this hotel for admin review before publishing.");
        else h.unpublish();
        hotels.flush();
        return hotelView(h);
    }
    public void requireReviewReady(Hotel hotel){
        if(hotel.getDescription().isBlank())throw conflict("Add a hotel description before submitting for review.");
        if(rooms.countByHotelIdAndActiveTrue(hotel.getId())==0)throw conflict("Add an active room type before submitting for review.");
        if(images.countByHotelIdAndRoomTypeIsNull(hotel.getId())==0)throw conflict("Upload a hotel photo before submitting for review.");
        if(inventory.sellableFutureNights(hotel.getId(),LocalDate.now(java.time.ZoneId.of(hotel.getTimeZone())))==0)
            throw conflict("Initialize future sellable inventory before submitting for review.");
    }
    @Transactional public HotelView submit(Long id,String email){
        var h=ownerHotel(id,email,true);
        if(h.getStatus()!=HotelStatus.DRAFT||h.getReviewStatus().equals("PENDING"))throw conflict("Only a draft or rejected hotel can be submitted for review.");
        requireReviewReady(h);h.submitForReview();hotels.flush();
        notifications.event(h.getHost().getId(),"hotel-submitted:"+id+":"+h.getVersion(),"Hotel submitted for review",h.getName()+" is awaiting platform review.","/host/hotels");
        return hotelView(h);
    }
    public List<RoomView> ownRooms(Long hotelId,String email) {
        ownedHotel(hotelId,email,false); return rooms.findByHotelIdOrderById(hotelId).stream().map(this::roomView).toList();
    }
    @Transactional public RoomView createRoom(Long hotelId,String email,RoomCreate in) {
        var hotel=ownedHotel(hotelId,email,true);
        if(hotel.getStatus()==HotelStatus.ARCHIVED) throw conflict("Archived hotels cannot be edited.");
        return roomView(rooms.save(new RoomType(hotel,in.code(),in.name(),in.description(),in.maxGuests(),in.beds(),in.totalRooms(),in.basePrice())));
    }
    @Transactional public RoomView editRoom(Long id,String email,RoomEdit in) {
        var r=ownedRoom(id,email,true); r.edit(in.name(),in.description(),in.basePrice(),in.active()); return roomView(r);
    }
    @Transactional public CreatedNights initialize(Long id,String email,DateRange range) {
        var r=ownedRoom(id,email,true);
        return new CreatedNights(stock.initializeNights(id,r.getHotel().getHost().getId(),range.from(),range.to()));
    }
    @Transactional public NightView editNight(Long id,LocalDate date,String email,NightEdit in) {
        var r=ownedRoom(id,email,true);
        stock.configureNight(id,r.getHotel().getHost().getId(),date,in.totalRooms(),in.blockedRooms(),in.nightlyPrice(),in.stopSell());
        return NightView.from(inventory.findByRoomTypeIdAndStayDate(id,date).orElseThrow());
    }
    public List<NightView> nights(Long id,String email,LocalDate from,LocalDate to) {
        ownedRoom(id,email,false); validateRange(from,to);
        return inventory.findNights(id,from,to).stream().map(NightView::from).toList();
    }
    public static long validateRange(LocalDate from,LocalDate to) {
        if(from==null || to==null) throw new IllegalArgumentException("Dates are required.");
        long n=ChronoUnit.DAYS.between(from,to);
        if(n<1 || n>366) throw new IllegalArgumentException("Choose 1–366 nights.");
        return n;
    }
    public static void validateStay(Hotel hotel,LocalDate checkIn,LocalDate checkOut,int quantity,int guests) {
        validateRange(checkIn,checkOut);
        if(checkIn.isBefore(LocalDate.now(ZoneId.of(hotel.getTimeZone())))) throw new IllegalArgumentException("Check-in cannot be in the past at the hotel.");
        if(quantity<1 || quantity>20 || guests<1 || guests>1000) throw new IllegalArgumentException("Choose 1–20 rooms and 1–1000 guests.");
    }
    public List<Availability> availability(Long hotelId,LocalDate checkIn,LocalDate checkOut,int quantity,int guests) {
        Hotel h=published(hotelId); validateStay(h,checkIn,checkOut,quantity,guests);
        var ids=stock.availableRoomTypes(hotelId,checkIn,checkOut,quantity,(guests+quantity-1)/quantity);
        return ids.stream().map(id -> {
            var r=rooms.findById(id).orElseThrow();
            var nights=inventory.findNights(id,checkIn,checkOut).stream().map(i -> new NightPrice(i.getStayDate(),i.getNightlyPrice())).toList();
            BigDecimal total=nights.stream().map(NightPrice::unitPrice).reduce(BigDecimal.ZERO,BigDecimal::add).multiply(BigDecimal.valueOf(quantity));
            return new Availability(id,r.getName(),checkIn,checkOut,quantity,guests,h.getCurrencyCode(),total,nights);
        }).toList();
    }
}
