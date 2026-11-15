package com.hotel.hotel_booking.account;

import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.hotel.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class AccountService {
    private final AvatarService avatars;
    private final UserRepository users;private final AccountProfileRepository profiles;
    private final FavoriteHotelRepository favorites;private final HotelRepository hotels;private final HotelImageRepository images;
    public AccountService(UserRepository users,AccountProfileRepository profiles,FavoriteHotelRepository favorites,HotelRepository hotels,HotelImageRepository images,AvatarService avatars){this.avatars=avatars;this.users=users;this.profiles=profiles;this.favorites=favorites;this.hotels=hotels;this.images=images;}
    public record ProfileEdit(@NotBlank @Size(max=80) String name,@Size(max=30) @Pattern(regexp="[+0-9() .-]*") String phone,@Size(max=100) String city,@Size(max=500) String bio) {}
    public record Profile(Long id,String name,String email,String role,String phone,String city,String bio,Instant createdAt,String avatarUrl) {}
    public record Favorite(Long hotelId,Instant savedAt,HotelDtos.HotelView hotel) {}
    private User user(String email,boolean lock){return (lock?users.findLockedByEmail(email):users.findByEmail(email)).orElseThrow(HotelManagementService::missing);}
    private String clean(String value){return value==null?"":value.strip();}
    private Profile view(User u,AccountProfile p){return new Profile(u.getId(),u.getName(),u.getEmail(),u.getRole().name().toLowerCase(Locale.ROOT),p==null?"":p.getPhone(),p==null?"":p.getCity(),p==null?"":p.getBio(),u.getCreatedAt(),avatars.url(u.getId()));}
    public Profile profile(String email){var u=user(email,false);return view(u,profiles.findById(u.getId()).orElse(null));}
    @Transactional public Profile edit(String email,ProfileEdit in){
        var u=user(email,true);u.changeName(in.name().strip());var p=profiles.findById(u.getId()).orElseGet(()->new AccountProfile(u));
        p.edit(clean(in.phone()),clean(in.city()),clean(in.bio()));profiles.save(p);return view(u,p);
    }
    private Favorite view(FavoriteHotel f){
        var h=f.getHotel();HotelDtos.HotelView hotel=null;
        if(h.getStatus()==HotelStatus.PUBLISHED)hotel=HotelDtos.HotelView.publicFrom(h,images.findFirstByHotelIdAndRoomTypeIsNullOrderByPositionAscIdAsc(h.getId()).map(i->"/api/media/"+i.getId()).orElse(null));
        return new Favorite(h.getId(),f.getCreatedAt(),hotel);
    }
    public List<Favorite> favorites(String email){return favorites.forUser(user(email,false).getId()).stream().map(this::view).toList();}
    @Transactional public Favorite save(String email,Long hotelId){
        var u=user(email,true);var existing=favorites.findByUserIdAndHotelId(u.getId(),hotelId);
        if(existing.isPresent())return view(existing.get());
        var h=hotels.findByIdAndStatus(hotelId,HotelStatus.PUBLISHED).orElseThrow(HotelManagementService::missing);
        if(favorites.countByUserId(u.getId())>=200)throw conflict("You can save up to 200 hotels. Remove a saved hotel before adding another.");
        return view(favorites.save(new FavoriteHotel(u,h)));
    }
    @Transactional public void remove(String email,Long hotelId){var u=user(email,true);favorites.findByUserIdAndHotelId(u.getId(),hotelId).ifPresent(favorites::delete);}
}
