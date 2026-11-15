package com.hotel.hotel_booking.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final RoomInventoryRepository inventory;
    public InventoryService(RoomTypeRepository rooms, RoomInventoryRepository inventory, HotelRepository hotels) {
        this.hotels = hotels;
        this.rooms = rooms; this.inventory = inventory;
    }
    private static long validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("Check-in and check-out are required.");
        long nights = ChronoUnit.DAYS.between(from, to);
        if (nights < 1 || nights > 366) throw new IllegalArgumentException("Choose a range of 1 to 366 nights.");
        return nights;
    }
    /** Creates missing nights only. Repeating this call never resets bookings, prices or closures. */
    @Transactional
    public int initializeNights(Long roomTypeId, Long hostId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        RoomType room = ownedRoom(roomTypeId, hostId);
        var existing = new HashSet<>(inventory.findNights(roomTypeId, from, to).stream().map(RoomInventory::getStayDate).toList());
        int created = 0;
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            if (existing.contains(date)) continue;
            inventory.save(new RoomInventory(room, date, room.getTotalRooms(), room.getBasePrice()));
            created++;
        }
        return created;
    }
    @Transactional
    public void configureNight(Long roomTypeId, Long hostId, LocalDate date, int totalRooms,
            int blockedRooms, BigDecimal price, boolean stopSell) {
        if (date == null) throw new IllegalArgumentException("Stay date is required.");
        RoomType room = ownedRoom(roomTypeId, hostId);
        var row = inventory.findNightsForUpdate(roomTypeId, date, date.plusDays(1)).stream().findFirst()
            .orElseGet(() -> new RoomInventory(room, date, totalRooms, price));
        row.configure(totalRooms, blockedRooms, price, stopSell);
        inventory.save(row);
    }
    private RoomType ownedRoom(Long id, Long hostId) {
        Long hotelId = rooms.findHotelId(id).orElseThrow(() -> new IllegalArgumentException("Room type not found."));
        hotels.findLockedById(hotelId).orElseThrow(() -> new IllegalArgumentException("Hotel not found."));
        var room = rooms.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Room type not found."));
        if (hostId == null || !room.getHotel().getHost().getId().equals(hostId))
            throw new SecurityException("Only the hotel's owner may configure its stock.");
        return room;
    }
    @Transactional(readOnly = true)
    public List<Long> availableRoomTypes(Long hotelId, LocalDate checkIn, LocalDate checkOut, int quantity, int guestsPerRoom) {
        long nights = validateRange(checkIn, checkOut);
        if (quantity < 1 || guestsPerRoom < 1) throw new IllegalArgumentException("Rooms and guests must be positive.");
        return inventory.findAvailableRoomTypeIds(hotelId, checkIn, checkOut, quantity, guestsPerRoom, nights);
    }
}
