package com.hotel.hotel_booking;

import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HotelInventoryTests {
    @Autowired UserRepository users;
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory;
    @Autowired InventoryService service;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    User host;
    Hotel hotel;
    RoomType room;
    final LocalDate start = LocalDate.of(2027, 1, 1);
    final BigDecimal price = new BigDecimal("250.00");

    @BeforeEach void fixture() {
        host = users.save(new User("Hotel Host", "host-inventory@example.com", "test-hash", Role.HOST));
        hotel = hotels.save(new Hotel(host, "STAY Test Hotel", "stay-test-hotel", "A hotel", "1 Test Street", "Kuala Lumpur", "MY", "MYR", "Asia/Kuala_Lumpur"));
        hotel.publish();
        room = rooms.save(new RoomType(hotel, "DELUXE", "Deluxe King", "King bed", 2, 1, 3, price));
        em.flush();
    }
    @Test void persistsRelationsAndExactMoney() {
        service.initializeNights(room.getId(), host.getId(), start, start.plusDays(2));
        em.flush(); em.clear();
        var loaded = inventory.findNights(room.getId(), start, start.plusDays(2));
        assertEquals(2, loaded.size());
        assertEquals("MYR", loaded.getFirst().getRoomType().getHotel().getCurrencyCode());
        assertEquals(host.getId(), loaded.getFirst().getRoomType().getHotel().getHost().getId());
        assertEquals(price, loaded.getFirst().getNightlyPrice());
        assertEquals(3, loaded.getFirst().getAvailableRooms());
    }
    @Test void uniqueRoomTypeAndDayIsEnforcedByDatabase() {
        inventory.saveAndFlush(new RoomInventory(room, start, 3, price));
        assertThrows(DataIntegrityViolationException.class, () -> inventory.saveAndFlush(new RoomInventory(room, start, 3, price)));
    }
    @Test void uniqueRoomCodeWithinHotelIsEnforced() {
        assertThrows(DataIntegrityViolationException.class, () -> rooms.saveAndFlush(new RoomType(hotel, "DELUXE", "Other name", "", 2, 1, 1, price)));
    }
    @Test void foreignKeysRejectOrphanedInventory() {
        var day = inventory.saveAndFlush(new RoomInventory(room, start, 3, price));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update stay_room_inventory set room_type_id = ? where id = ?", -1L, day.getId()));
    }
    @Test void databaseChecksRejectOversoldOrNegativeInventory() {
        var day = inventory.saveAndFlush(new RoomInventory(room, start, 3, price));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update stay_room_inventory set booked_rooms = 4 where id = ?", day.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update stay_room_inventory set nightly_price = -1 where id = ?", day.getId()));
    }
    @Test void missingNightsNeverCountAsAvailableAndCheckoutIsExcluded() {
        service.initializeNights(room.getId(), host.getId(), start, start.plusDays(1));
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(2), 1, 2).isEmpty());
        assertEquals(java.util.List.of(room.getId()), service.availableRoomTypes(hotel.getId(), start, start.plusDays(1), 1, 2));
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(1), 4, 2).isEmpty());
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(1), 1, 3).isEmpty());
    }
    @Test void blockedBookedAndClosedNightsAffectAvailability() {
        service.initializeNights(room.getId(), host.getId(), start, start.plusDays(2));
        var day = inventory.findByRoomTypeIdAndStayDate(room.getId(), start).orElseThrow();
        day.reserve(1);
        service.configureNight(room.getId(), host.getId(), start, 3, 1, price, false);
        assertEquals(1, day.getAvailableRooms());
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(2), 2, 2).isEmpty());
        assertEquals(1, service.availableRoomTypes(hotel.getId(), start, start.plusDays(2), 1, 2).size());
        service.configureNight(room.getId(), host.getId(), start, 3, 1, price, true);
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(2), 1, 2).isEmpty());
        assertThrows(IllegalStateException.class, () -> day.reserve(1));
    }
    @Test void repeatedInitializationPreservesExistingBookingsAndPrices() {
        assertEquals(2, service.initializeNights(room.getId(), host.getId(), start, start.plusDays(2)));
        var day = inventory.findByRoomTypeIdAndStayDate(room.getId(), start).orElseThrow();
        day.reserve(1);
        service.configureNight(room.getId(), host.getId(), start, 2, 1, new BigDecimal("300.00"), true);
        assertEquals(1, service.initializeNights(room.getId(), host.getId(), start, start.plusDays(3)));
        assertEquals(1, day.getBookedRooms());
        assertEquals(1, day.getBlockedRooms());
        assertEquals(new BigDecimal("300.00"), day.getNightlyPrice());
        assertTrue(day.isStopSell());
    }
    @Test void invalidOwnershipAndStockChangesAreRejected() {
        assertThrows(SecurityException.class, () -> service.initializeNights(room.getId(), -1L, start, start.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.availableRoomTypes(hotel.getId(), start, start, 1, 1));
        var day = new RoomInventory(room, start, 3, price);
        day.reserve(2);
        assertThrows(IllegalArgumentException.class, () -> day.configure(1, 0, price, false));
        assertThrows(IllegalArgumentException.class, () -> day.configure(3, 2, price, false));
        assertThrows(IllegalArgumentException.class, () -> day.configure(4, 0, price, false));
        assertThrows(IllegalStateException.class, () -> day.reserve(2));
        assertThrows(IllegalArgumentException.class, () -> day.release(3));
        day.release(1);
        assertEquals(2, day.getAvailableRooms());
    }
    @Test void unpublishedOrInactiveRoomsAreNotAvailable() {
        service.initializeNights(room.getId(), host.getId(), start, start.plusDays(1));
        room.deactivate();
        assertTrue(service.availableRoomTypes(hotel.getId(), start, start.plusDays(1), 1, 2).isEmpty());
        hotel.archive();
        assertThrows(IllegalStateException.class, hotel::publish);
    }
    @Test void staleInventoryWritersAreRejected() {
        var day = inventory.saveAndFlush(new RoomInventory(room, start, 3, price));
        jdbc.update("update stay_room_inventory set version = version + 1 where id = ?", day.getId());
        day.reserve(1);
        assertThrows(OptimisticLockException.class, em::flush);
    }
    @Test void onlyHostsCanOwnHotelsAndMoneyIsNotRoundedSilently() {
        var guest = new User("Guest", "guest@example.com", "hash", Role.USER);
        assertThrows(IllegalArgumentException.class, () -> new Hotel(guest, "Hotel", "hotel", "", "Address", "City", "MY", "MYR", "Asia/Kuala_Lumpur"));
        assertThrows(IllegalArgumentException.class, () -> new RoomInventory(room, start, 1, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> new RoomInventory(room, start, 1, new BigDecimal("-1")));
    }
}
