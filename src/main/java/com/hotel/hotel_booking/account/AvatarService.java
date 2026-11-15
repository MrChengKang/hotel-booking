package com.hotel.hotel_booking.account;

import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.hotel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import static com.hotel.hotel_booking.hotel.HotelManagementService.missing;

@Service @Transactional(readOnly=true)
public class AvatarService {
    private final UserRepository users;
    private final AccountAvatarRepository avatars;
    private final ImageStorage storage;
    public AvatarService(UserRepository users,AccountAvatarRepository avatars,ImageStorage storage){this.users=users;this.avatars=avatars;this.storage=storage;}
    public record View(String avatarUrl) {}
    public record Media(byte[] bytes,String type) {}
    public String url(Long userId){return avatars.findById(userId).map(AccountAvatar::url).orElse(null);}
    private User user(String email,boolean lock){return (lock?users.findLockedByEmail(email):users.findByEmail(email)).orElseThrow(HotelManagementService::missing);}
    @Transactional public View upload(String email,MultipartFile file){
        var user=user(email,true);var existing=avatars.findById(user.getId()).orElse(null);
        String previous=existing==null?null:existing.getStorageKey();var stored=storage.store(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.delete(stored.key());}
            @Override public void afterCommit(){if(previous!=null)storage.delete(previous);}
        });
        var avatar=existing==null?new AccountAvatar(user,stored):existing;
        if(existing!=null)avatar.replace(stored);
        return new View(avatars.saveAndFlush(avatar).url());
    }
    @Transactional public void remove(String email){
        var user=user(email,true);var avatar=avatars.findById(user.getId()).orElse(null);
        if(avatar==null)return;
        String key=avatar.getStorageKey();avatars.delete(avatar);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){storage.delete(key);}});
    }
    public Media media(String email,String token){
        var avatar=avatars.findById(user(email,false).getId()).orElseThrow(HotelManagementService::missing);
        if(!avatar.token().equals(token))throw missing();
        return new Media(storage.read(avatar.getStorageKey()),avatar.getContentType());
    }
}
