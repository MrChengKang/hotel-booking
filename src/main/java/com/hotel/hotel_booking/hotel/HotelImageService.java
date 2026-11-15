package com.hotel.hotel_booking.hotel;

import java.util.*;
import java.security.Principal;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;

@Service @Transactional(readOnly=true)
public class HotelImageService {
    private final HotelManagementService management; private final HotelImageRepository images;
    private final RoomTypeRepository rooms;private final ImageStorage storage;
    public HotelImageService(HotelManagementService management,HotelImageRepository images,RoomTypeRepository rooms,ImageStorage storage){this.management=management;this.images=images;this.rooms=rooms;this.storage=storage;}
    public record View(Long id,Long hotelId,Long roomTypeId,String url,String altText,int position,int width,int height,long byteSize) {
        public static View from(HotelImage i){return new View(i.getId(),i.getHotel().getId(),i.getRoomType()==null?null:i.getRoomType().getId(),"/api/media/"+i.getId(),i.getAltText(),i.getPosition(),i.getWidth(),i.getHeight(),i.getByteSize());}
    }
    public record Order(Long roomTypeId,@NotNull @Size(max=20) List<@NotNull Long> imageIds) {}
    public record Caption(@NotNull @Size(max=120) String altText) {}
    private RoomType room(Long hotelId,Long roomId){if(roomId==null)return null;var room=rooms.findById(roomId).orElseThrow(HotelManagementService::missing);if(!room.getHotel().getId().equals(hotelId))throw missing();return room;}
    private Hotel owned(Long id,String email,boolean write){var hotel=management.ownedHotel(id,email,write);if(write&&hotel.getStatus()==HotelStatus.ARCHIVED)throw conflict("Archived hotels cannot be edited.");return hotel;}
    private String alt(String text){if(text==null)return "";if(text.length()>120)throw new IllegalArgumentException();return text.strip();}
    public List<View> own(Long id,Long roomId,String email){owned(id,email,false);room(id,roomId);return images.scope(id,roomId).stream().map(View::from).toList();}
    public List<View> publicImages(Long id,Long roomId){management.published(id);var r=room(id,roomId);if(r!=null&&!r.isActive())throw missing();return images.scope(id,roomId).stream().map(View::from).toList();}
    @Transactional public View upload(Long id,Long roomId,String email,MultipartFile file,String caption){
        var hotel=owned(id,email,true);var r=room(id,roomId);var existing=images.scope(id,roomId);
        if(existing.size()>=20)throw conflict("Each hotel or room type can have up to 20 photos.");
        String text=alt(caption);var stored=storage.store(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.delete(stored.key());}});
        int position=existing.stream().mapToInt(HotelImage::getPosition).max().orElse(-1)+1;
        return View.from(images.saveAndFlush(new HotelImage(hotel,r,stored,text,position)));
    }
    private HotelImage image(Long hotelId,Long id){var image=images.findById(id).orElseThrow(HotelManagementService::missing);if(!image.getHotel().getId().equals(hotelId))throw missing();return image;}
    @Transactional public List<View> reorder(Long id,String email,Order order){
        owned(id,email,true);room(id,order.roomTypeId());var scope=images.scope(id,order.roomTypeId());
        var ids=order.imageIds();if(ids.size()!=scope.size()||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(new HashSet<>(scope.stream().map(HotelImage::getId).toList())))throw conflict("Photos changed. Refresh before reordering.");
        var byId=new HashMap<Long,HotelImage>();scope.forEach(i->byId.put(i.getId(),i));
        for(int n=0;n<ids.size();n++)byId.get(ids.get(n)).setPosition(n);
        return ids.stream().map(i->View.from(byId.get(i))).toList();
    }
    @Transactional public View caption(Long hotelId,Long id,String email,Caption in){owned(hotelId,email,true);var i=image(hotelId,id);i.setAltText(alt(in.altText()));return View.from(i);}
    @Transactional public void delete(Long hotelId,Long id,String email){owned(hotelId,email,true);var i=image(hotelId,id);String key=i.getStorageKey();images.delete(i);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){storage.delete(key);}});}
    public record Media(byte[] bytes,String type) {}
    public Media media(Long id,Principal principal){
        var i=images.findById(id).orElseThrow(HotelManagementService::missing);
        if(i.getHotel().getStatus()!=HotelStatus.PUBLISHED||(i.getRoomType()!=null&&!i.getRoomType().isActive())){
            if(principal==null)throw missing();
            // Return 404 for every caller who is not the owning host.
            if(!i.getHotel().getHost().getEmail().equals(principal.getName())&&!management.administrator(principal.getName()))throw missing();
        }
        return new Media(storage.read(i.getStorageKey()),i.getContentType());
    }
}
