package com.hotel.hotel_booking.hotel;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

public final class HotelDtos {
    private HotelDtos() {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
    public record HotelCreate(@NotBlank @Size(max=160) String name, @NotBlank @Size(max=180) String slug,
        @Size(max=10000) String description, @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=100) String city, @NotBlank @Size(min=2,max=2) String countryCode,
        @NotBlank @Size(min=3,max=3) String currencyCode, @NotBlank @Size(max=64) String timeZone,
        @Size(max=8) List<@NotNull HotelAmenity> amenities) {
        public HotelCreate(String name,String slug,String description,String address,String city,String countryCode,String currencyCode,String timeZone) {
            this(name,slug,description,address,city,countryCode,currencyCode,timeZone,null);
        }
    }
    public record HotelEdit(@NotBlank @Size(max=160) String name, @Size(max=10000) String description,
        @NotBlank @Size(max=500) String address, @NotBlank @Size(max=100) String city,
        @Size(max=8) List<@NotNull HotelAmenity> amenities) {
        public HotelEdit(String name,String description,String address,String city) { this(name,description,address,city,null); }
    }
    public record StatusChange(@NotNull HotelStatus status, String coverImageUrl) {}
    public record RoomCreate(@NotBlank @Size(max=40) String code, @NotBlank @Size(max=120) String name,
        @Size(max=10000) String description, @Min(1) int maxGuests, @Min(1) int beds,
        @Min(1) int totalRooms, @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal basePrice) {}
    public record RoomEdit(@NotBlank @Size(max=120) String name, @Size(max=10000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal basePrice, boolean active) {}
    public record DateRange(@NotNull LocalDate from, @NotNull LocalDate to) {}
    public record NightEdit(@Min(0) int totalRooms, @Min(0) int blockedRooms,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal nightlyPrice, boolean stopSell) {}
    public record CreatedNights(int createdNights) {}
    public record HotelView(Long id, String name, String slug, String description, String address,
        String city, String countryCode, String currencyCode, String timeZone, HotelStatus status, String coverImageUrl,
        String reviewStatus,String reviewNote,java.time.Instant submittedAt,java.time.Instant reviewedAt,long version,
        List<HotelAmenity> amenities,BigDecimal startingNightlyPrice,Long roomTypeId) {
        public HotelView withSearchPrice(BigDecimal price,Long roomId) {
            return new HotelView(id,name,slug,description,address,city,countryCode,currencyCode,timeZone,status,coverImageUrl,
                reviewStatus,reviewNote,submittedAt,reviewedAt,version,amenities,price,roomId);
        }
        public static HotelView from(Hotel h) { return from(h,null); }
        public static HotelView from(Hotel h,String cover) {
            return new HotelView(h.getId(),h.getName(),h.getSlug(),h.getDescription(),h.getAddress(),h.getCity(),h.getCountryCode(),h.getCurrencyCode(),h.getTimeZone(),h.getStatus(),cover,h.getReviewStatus(),h.getReviewNote(),h.getSubmittedAt(),h.getReviewedAt(),h.getVersion(),h.getAmenities().stream().sorted().toList(),null,null);
        }
        public static HotelView publicFrom(Hotel h,String cover){
            return new HotelView(h.getId(),h.getName(),h.getSlug(),h.getDescription(),h.getAddress(),h.getCity(),h.getCountryCode(),h.getCurrencyCode(),h.getTimeZone(),h.getStatus(),cover,null,null,null,null,0,h.getAmenities().stream().sorted().toList(),null,null);
        }
    }
    public record RoomView(Long id, Long hotelId, String code, String name, String description,
        int maxGuests, int beds, int totalRooms, BigDecimal basePrice, String currencyCode, boolean active, String coverImageUrl) {
        public static RoomView from(RoomType r) { return from(r,null); }
        public static RoomView from(RoomType r,String cover) {
            return new RoomView(r.getId(),r.getHotel().getId(),r.getCode(),r.getName(),r.getDescription(),r.getMaxGuests(),r.getBeds(),r.getTotalRooms(),r.getBasePrice(),r.getHotel().getCurrencyCode(),r.isActive(),cover);
        }
    }
    public record NightView(LocalDate date, int totalRooms, int bookedRooms, int blockedRooms,
        int availableRooms, BigDecimal nightlyPrice, boolean stopSell) {
        public static NightView from(RoomInventory i) {
            return new NightView(i.getStayDate(),i.getTotalRooms(),i.getBookedRooms(),i.getBlockedRooms(),i.getAvailableRooms(),i.getNightlyPrice(),i.isStopSell());
        }
    }
    public record Availability(Long roomTypeId, String roomName, LocalDate checkIn, LocalDate checkOut,
        int rooms, int guests, String currencyCode, BigDecimal total, List<NightPrice> nights) {}
    public record NightPrice(LocalDate date, BigDecimal unitPrice) {}
}
