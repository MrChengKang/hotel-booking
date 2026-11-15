package com.hotel.hotel_booking.hotel;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Explicit composite primary key also satisfies Aiven's sql_require_primary_key. */
@Entity
@Table(name = "stay_hotel_amenities")
@IdClass(HotelFacility.Key.class)
public class HotelFacility {
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_stay_hotel_amenities_hotel"))
    private Hotel hotel;

    @Id @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "amenity", nullable = false, length = 32)
    private HotelAmenity amenity;

    protected HotelFacility() {}
    public HotelFacility(Hotel hotel, HotelAmenity amenity) {
        this.hotel = Objects.requireNonNull(hotel);
        this.amenity = Objects.requireNonNull(amenity);
    }
    public HotelAmenity getAmenity() { return amenity; }

    public static class Key implements Serializable {
        private Long hotel;
        private HotelAmenity amenity;
        public Key() {}
        public Key(Long hotel, HotelAmenity amenity) { this.hotel = hotel; this.amenity = amenity; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Key key && Objects.equals(hotel, key.hotel) && amenity == key.amenity;
        }
        @Override public int hashCode() { return Objects.hash(hotel, amenity); }
    }
}
