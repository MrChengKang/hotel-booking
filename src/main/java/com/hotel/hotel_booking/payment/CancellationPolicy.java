package com.hotel.hotel_booking.payment;

import java.time.*;

public final class CancellationPolicy {
    private CancellationPolicy() {}
    public static Instant deadline(LocalDate checkIn, String hotelTimeZone) {
        return checkIn.atStartOfDay(ZoneId.of(hotelTimeZone)).toInstant();
    }
    public static boolean beforeCheckIn(Instant now, LocalDate checkIn, String hotelTimeZone) {
        return now.isBefore(deadline(checkIn, hotelTimeZone));
    }
}
