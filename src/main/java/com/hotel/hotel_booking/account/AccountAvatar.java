package com.hotel.hotel_booking.account;

import com.hotel.hotel_booking.hotel.ImageStorage;
import com.hotel.hotel_booking.user.User;
import jakarta.persistence.*;

@Entity @Table(name="stay_account_avatars")
public class AccountAvatar {
    @Id private Long userId;
    @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
    @Column(nullable=false,unique=true,length=50) private String storageKey;
    @Column(nullable=false,length=20) private String contentType;
    protected AccountAvatar() {}
    public AccountAvatar(User user,ImageStorage.Stored stored){this.user=user;replace(stored);}
    public void replace(ImageStorage.Stored stored){storageKey=stored.key();contentType=stored.type();}
    public String getStorageKey(){return storageKey;}
    public String getContentType(){return contentType;}
    public String token(){return storageKey.substring(0,storageKey.lastIndexOf('.')).replaceFirst("^cld-", "");}
    public String url(){return "/api/account/avatar/"+token();}
}
