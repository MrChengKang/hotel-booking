package com.hotel.hotel_booking.hotel;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "stay_room_types",
    uniqueConstraints = @UniqueConstraint(name = "uk_stay_room_type_code", columnNames = {"hotel_id", "code"}),
    indexes = @Index(name = "ix_stay_room_types_hotel", columnList = "hotel_id,active"))
@Check(name = "ck_stay_room_type_values", constraints = "max_guests > 0 and beds > 0 and total_rooms > 0 and base_price >= 0")
public class RoomType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stay_room_types_hotel"))
    private Hotel hotel;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "max_guests", nullable = false) private int maxGuests;
    @Column(nullable = false) private int beds;
    @Column(name = "total_rooms", nullable = false) private int totalRooms;
    @Column(name = "base_price", nullable = false, precision = 12, scale = 2) private BigDecimal basePrice;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected RoomType() {}
    public RoomType(Hotel hotel, String code, String name, String description, int maxGuests,
            int beds, int totalRooms, BigDecimal basePrice) {
        if (hotel == null) throw new IllegalArgumentException("A room type must belong to a hotel.");
        if (maxGuests < 1 || beds < 1 || totalRooms < 1) throw new IllegalArgumentException("Guest capacity, beds and total rooms must be positive.");
        this.hotel = hotel;
        this.code = ModelRules.text(code, "Room code", 40).toUpperCase(Locale.ROOT);
        if (!this.code.matches("[A-Z0-9_-]+")) throw new IllegalArgumentException("Room code must use letters, numbers, underscores or hyphens.");
        this.name = ModelRules.text(name, "Room name", 120);
        this.description = description == null ? "" : description.strip();
        if (this.description.length() > 10000) throw new IllegalArgumentException("Description must be at most 10000 characters.");
        this.maxGuests = maxGuests; this.beds = beds; this.totalRooms = totalRooms;
        this.basePrice = ModelRules.money(basePrice);
    }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public void edit(String name,String description,BigDecimal price,boolean active) {
        var checked=new RoomType(hotel,code,name,description,maxGuests,beds,totalRooms,price);
        this.name=checked.name; this.description=checked.description; this.basePrice=checked.basePrice; this.active=active;
    }
    public void deactivate() { active = false; }
    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getMaxGuests() { return maxGuests; }
    public int getBeds() { return beds; }
    public int getTotalRooms() { return totalRooms; }
    public BigDecimal getBasePrice() { return basePrice; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
