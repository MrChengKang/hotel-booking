package com.hotel.hotel_booking.booking;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class BookingDtos {
    private BookingDtos(){}
    public record Create(@NotNull @Positive Long roomTypeId,@NotNull LocalDate checkIn,@NotNull LocalDate checkOut,
        @Min(1) @Max(20) int rooms,@Min(1) @Max(1000) int guests,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
        @DecimalMin("0.00") @Digits(integer=14,fraction=2) BigDecimal expectedTotal) {}
    public record Summary(Long id,String reference,Long hotelId,Long roomTypeId,String hotelName,String roomName,
        LocalDate checkIn,LocalDate checkOut,int rooms,int guests,String currencyCode,BigDecimal total,
        BookingStatus status,String paymentStatus,Instant createdAt,Instant cancelledAt,Instant expiresAt) {
        public static Summary from(Booking b){return new Summary(b.getId(),b.getReference(),b.getRoomType().getHotel().getId(),b.getRoomType().getId(),b.getHotelName(),b.getRoomName(),b.getCheckIn(),b.getCheckOut(),b.getQuantity(),b.getGuests(),b.getCurrencyCode(),b.getTotal(),b.getStatus(),b.getPaymentStatus(),b.getCreatedAt(),b.getCancelledAt(),b.getExpiresAt());}
    }
    public record Night(LocalDate date,BigDecimal unitPrice){}
    public record Detail(Summary booking,List<Night> nights){public static Detail from(Booking b){return new Detail(Summary.from(b),b.getNights().stream().map(n->new Night(n.getStayDate(),n.getUnitPrice())).toList());}}
    public record HostBooking(Summary booking,String guestName){public static HostBooking from(Booking b){return new HostBooking(Summary.from(b),b.getGuest().getName());}}
}
