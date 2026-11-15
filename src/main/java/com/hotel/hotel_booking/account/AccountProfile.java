package com.hotel.hotel_booking.account;

import com.hotel.hotel_booking.user.User;
import jakarta.persistence.*;

@Entity @Table(name="stay_account_profiles")
public class AccountProfile {
    @Id private Long userId;
    @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @Column(nullable=false,length=30) private String phone="";
    @Column(nullable=false,length=100) private String city="";
    @Column(nullable=false,length=500) private String bio="";
    protected AccountProfile() {}
    public AccountProfile(User user){this.user=user;}
    public String getPhone(){return phone;} public String getCity(){return city;} public String getBio(){return bio;}
    public void edit(String phone,String city,String bio){this.phone=phone;this.city=city;this.bio=bio;}
}
