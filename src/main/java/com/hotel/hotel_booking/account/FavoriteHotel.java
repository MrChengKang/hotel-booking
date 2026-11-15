package com.hotel.hotel_booking.account;

import com.hotel.hotel_booking.user.User;
import com.hotel.hotel_booking.hotel.Hotel;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="stay_favorite_hotels",uniqueConstraints=@UniqueConstraint(name="uk_stay_favorite_user_hotel",columnNames={"user_id","hotel_id"}),indexes=@Index(name="ix_stay_favorite_user",columnList="user_id,created_at"))
public class FavoriteHotel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="hotel_id",nullable=false) private Hotel hotel;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected FavoriteHotel() {}
    public FavoriteHotel(User user,Hotel hotel){this.user=user;this.hotel=hotel;createdAt=Instant.now();}
    public Hotel getHotel(){return hotel;} public Instant getCreatedAt(){return createdAt;}
}
