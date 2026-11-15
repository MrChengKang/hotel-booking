package com.hotel.hotel_booking.booking;

import com.hotel.hotel_booking.hotel.RoomType;
import com.hotel.hotel_booking.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="stay_bookings", uniqueConstraints={
    @UniqueConstraint(name="uk_stay_booking_request",columnNames={"guest_id","request_key"}),
    @UniqueConstraint(name="uk_stay_booking_reference",columnNames="reference")
},indexes={@Index(name="ix_stay_booking_guest",columnList="guest_id,created_at"),@Index(name="ix_stay_booking_room",columnList="room_type_id,check_in,status")})
@Check(name="ck_stay_booking_values",constraints="quantity between 1 and 20 and guests > 0 and total >= 0 and check_out > check_in and status in ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED')")
public class Booking {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=36) private String reference;
    @Column(name="request_key",nullable=false,length=36) private String requestKey;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="guest_id",nullable=false,foreignKey=@ForeignKey(name="fk_stay_booking_guest")) private User guest;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_type_id",nullable=false,foreignKey=@ForeignKey(name="fk_stay_booking_room")) private RoomType roomType;
    @Column(name="hotel_name",nullable=false,length=160) private String hotelName;
    @Column(name="room_name",nullable=false,length=120) private String roomName;
    @Column(name="hotel_time_zone",nullable=false,length=64) private String hotelTimeZone;
    @Column(name="currency_code",nullable=false,length=3) private String currencyCode;
    @Column(name="check_in",nullable=false) private LocalDate checkIn;
    @Column(name="check_out",nullable=false) private LocalDate checkOut;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false) private int guests;
    @Column(nullable=false,precision=16,scale=2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=16) private BookingStatus status=BookingStatus.CONFIRMED;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false,length=16) private String paymentStatus="UNPAID";
    private Instant expiresAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Version @Column(nullable=false) private long version;
    @OneToMany(mappedBy="booking",cascade=CascadeType.PERSIST) @OrderBy("stayDate ASC")
    private List<BookingNight> nights=new ArrayList<>();
    protected Booking() {}
    public Booking(User guest,RoomType room,String requestKey,LocalDate checkIn,LocalDate checkOut,int quantity,int guests) {
        this.guest=guest; this.roomType=room; this.requestKey=requestKey; this.reference=UUID.randomUUID().toString();
        this.hotelName=room.getHotel().getName(); this.roomName=room.getName(); this.hotelTimeZone=room.getHotel().getTimeZone();
        this.currencyCode=room.getHotel().getCurrencyCode(); this.checkIn=checkIn; this.checkOut=checkOut; this.quantity=quantity; this.guests=guests;
        this.total=new BigDecimal("0.00");
    }
    public void addNight(LocalDate date,BigDecimal price) { nights.add(new BookingNight(this,date,price)); total=total.add(price.multiply(BigDecimal.valueOf(quantity))); }
    public void hold(Instant until){status=BookingStatus.PENDING_PAYMENT;expiresAt=until;}
    public void extendHold(Instant until){expiresAt=until;}
    public void paymentConfirmed(){status=BookingStatus.CONFIRMED;paymentStatus="PAID";expiresAt=null;}
    public void freeConfirmed(){status=BookingStatus.CONFIRMED;paymentStatus="NOT_REQUIRED";expiresAt=null;}
    public void paymentNeedsReview(){paymentStatus="REVIEW_REQUIRED";}
    public void refundPending(){paymentStatus="REFUND_PENDING";}
    public void refundFailed(){paymentStatus="REFUND_FAILED";}
    public void refunded(){cancel();paymentStatus="REFUNDED";}
    public void expire(){status=BookingStatus.EXPIRED;expiresAt=null;}
    public String getPaymentStatus(){return paymentStatus;} public Instant getExpiresAt(){return expiresAt;}
    public void cancel() { status=BookingStatus.CANCELLED; cancelledAt=Instant.now();expiresAt=null; }
    public Long getId(){return id;} public String getReference(){return reference;} public String getRequestKey(){return requestKey;}
    public User getGuest(){return guest;} public RoomType getRoomType(){return roomType;} public String getHotelName(){return hotelName;}
    public String getRoomName(){return roomName;} public String getHotelTimeZone(){return hotelTimeZone;} public String getCurrencyCode(){return currencyCode;}
    public LocalDate getCheckIn(){return checkIn;} public LocalDate getCheckOut(){return checkOut;} public int getQuantity(){return quantity;} public int getGuests(){return guests;}
    public BigDecimal getTotal(){return total;} public BookingStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
    public Instant getCancelledAt(){return cancelledAt;} public List<BookingNight> getNights(){return Collections.unmodifiableList(nights);}
}
