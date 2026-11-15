package com.hotel.hotel_booking.hotel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "stay_room_inventory",
    uniqueConstraints = @UniqueConstraint(name = "uk_stay_inventory_room_date", columnNames = {"room_type_id", "stay_date"}),
    indexes = @Index(name = "ix_stay_inventory_date", columnList = "stay_date,room_type_id"))
@Check(name = "ck_stay_inventory_values", constraints = "total_rooms >= 0 and booked_rooms >= 0 and blocked_rooms >= 0 and booked_rooms + blocked_rooms <= total_rooms and nightly_price >= 0")
public class RoomInventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stay_inventory_room_type"))
    private RoomType roomType;
    @Column(name = "stay_date", nullable = false) private LocalDate stayDate;
    @Column(name = "total_rooms", nullable = false) private int totalRooms;
    @Column(name = "booked_rooms", nullable = false) private int bookedRooms = 0;
    @Column(name = "blocked_rooms", nullable = false) private int blockedRooms = 0;
    @Column(name = "nightly_price", nullable = false, precision = 12, scale = 2) private BigDecimal nightlyPrice;
    @Column(name = "stop_sell", nullable = false) private boolean stopSell = false;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected RoomInventory() {}
    public RoomInventory(RoomType roomType, LocalDate stayDate, int totalRooms, BigDecimal nightlyPrice) {
        if (roomType == null || stayDate == null) throw new IllegalArgumentException("Room type and stay date are required.");
        this.roomType = roomType; this.stayDate = stayDate;
        configure(totalRooms, 0, nightlyPrice, false);
    }
    @PrePersist @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void configure(int totalRooms, int blockedRooms, BigDecimal price, boolean stopSell) {
        if (totalRooms < 0 || totalRooms > roomType.getTotalRooms() || blockedRooms < 0 || blockedRooms > totalRooms - bookedRooms)
            throw new IllegalArgumentException("Daily stock must cover booked/blocked rooms and cannot exceed room-type capacity.");
        BigDecimal validatedPrice = ModelRules.money(price);
        this.totalRooms = totalRooms; this.blockedRooms = blockedRooms;
        this.nightlyPrice = validatedPrice; this.stopSell = stopSell;
    }
    // Building blocks for a later booking transaction; not a public booking API.
    public void reserve(int quantity) {
        if (quantity < 1 || stopSell || quantity > getAvailableRooms()) throw new IllegalStateException("Not enough rooms available.");
        bookedRooms += quantity;
    }
    public void release(int quantity) {
        if (quantity < 1 || quantity > bookedRooms) throw new IllegalArgumentException("Release quantity exceeds booked rooms.");
        bookedRooms -= quantity;
    }
    public Long getId() { return id; }
    public RoomType getRoomType() { return roomType; }
    public LocalDate getStayDate() { return stayDate; }
    public int getTotalRooms() { return totalRooms; }
    public int getBookedRooms() { return bookedRooms; }
    public int getBlockedRooms() { return blockedRooms; }
    public int getAvailableRooms() { return totalRooms - bookedRooms - blockedRooms; }
    public BigDecimal getNightlyPrice() { return nightlyPrice; }
    public boolean isStopSell() { return stopSell; }
    public long getVersion() { return version; }
}
