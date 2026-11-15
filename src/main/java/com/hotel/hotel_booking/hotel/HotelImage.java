package com.hotel.hotel_booking.hotel;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="stay_hotel_images", indexes=@Index(name="ix_stay_images_scope",columnList="hotel_id,room_type_id,sort_order"))
public class HotelImage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="hotel_id",nullable=false) private Hotel hotel;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="room_type_id") private RoomType roomType;
    @Column(nullable=false,unique=true,length=50) private String storageKey;
    @Column(nullable=false,length=20) private String contentType;
    @Column(nullable=false) private long byteSize;
    @Column(nullable=false) private int width;
    @Column(nullable=false) private int height;
    @Column(nullable=false,length=120) private String altText;
    @Column(name="sort_order",nullable=false) private int position;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected HotelImage() {}
    public HotelImage(Hotel hotel,RoomType room,ImageStorage.Stored stored,String alt,int position) {
        this.hotel=hotel;roomType=room;storageKey=stored.key();contentType=stored.type();
        byteSize=stored.size();width=stored.width();height=stored.height();altText=alt;this.position=position;createdAt=Instant.now();
    }
    public Long getId(){return id;} public Hotel getHotel(){return hotel;} public RoomType getRoomType(){return roomType;}
    public String getStorageKey(){return storageKey;} public String getContentType(){return contentType;}
    public long getByteSize(){return byteSize;} public int getWidth(){return width;} public int getHeight(){return height;}
    public String getAltText(){return altText;} public int getPosition(){return position;}
    public void setPosition(int position){this.position=position;} public void setAltText(String alt){altText=alt;}
}
