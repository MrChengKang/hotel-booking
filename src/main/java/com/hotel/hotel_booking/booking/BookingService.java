package com.hotel.hotel_booking.booking;

import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.hotel.hotel_booking.hotel.HotelManagementService.*;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;
import static com.hotel.hotel_booking.booking.BookingDtos.*;

@Service
@Transactional(readOnly=true)
public class BookingService {
    private final com.hotel.hotel_booking.notification.NotificationService notifications;
    private final com.hotel.hotel_booking.payment.PaymentSettings payments;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final RoomInventoryRepository inventory;
    private final HotelManagementService management;
    public BookingService(BookingRepository bookings,UserRepository users,HotelRepository hotels,RoomTypeRepository rooms,RoomInventoryRepository inventory,HotelManagementService management,com.hotel.hotel_booking.notification.NotificationService notifications,com.hotel.hotel_booking.payment.PaymentSettings payments){this.notifications=notifications;this.payments=payments;
        this.bookings=bookings;this.users=users;this.hotels=hotels;this.rooms=rooms;this.inventory=inventory;this.management=management;
    }
    private User lockedGuest(String email){
        var user=users.findLockedByEmail(com.hotel.hotel_booking.auth.AuthService.normalizeEmail(email)).orElseThrow(HotelManagementService::missing);
        if(user.getRole()!=Role.USER) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Only User accounts can book rooms.");
        return user;
    }
    private RoomType lockedRoom(Long roomTypeId){
        Long hotelId=rooms.findHotelId(roomTypeId).orElseThrow(HotelManagementService::missing);
        hotels.findLockedById(hotelId).orElseThrow(HotelManagementService::missing);
        return rooms.findLockedById(roomTypeId).orElseThrow(HotelManagementService::missing);
    }
    @Transactional public Detail create(String email,Create in){
        User guest=lockedGuest(email);
        String key;
        try { key=UUID.fromString(in.requestKey()).toString(); }
        catch(RuntimeException invalid){throw new IllegalArgumentException("requestKey must be a UUID.");}
        var existing=bookings.findByGuestIdAndRequestKey(guest.getId(),key);
        if(existing.isPresent()){
            var b=existing.get();
            if(!b.getRoomType().getId().equals(in.roomTypeId()) || !b.getCheckIn().equals(in.checkIn()) || !b.getCheckOut().equals(in.checkOut()) || b.getQuantity()!=in.rooms() || b.getGuests()!=in.guests())
                throw conflict("This requestKey was already used for a different booking.");
            return Detail.from(b);
        }
        RoomType room=lockedRoom(in.roomTypeId());
        validateStay(room.getHotel(),in.checkIn(),in.checkOut(),in.rooms(),in.guests());
        if(!room.isActive() || room.getHotel().getStatus()!=HotelStatus.PUBLISHED) throw conflict("This room is not available for booking.");
        if(in.guests()>(long)room.getMaxGuests()*in.rooms()) throw new IllegalArgumentException("Guest count exceeds room capacity.");
        var nights=inventory.findNightsForUpdate(room.getId(),in.checkIn(),in.checkOut());
        if(nights.size()!=validateRange(in.checkIn(),in.checkOut())) throw conflict("Inventory is missing for one or more nights.");
        for(var night:nights) if(night.isStopSell() || night.getAvailableRooms()<in.rooms()) throw conflict("Not enough rooms available for the entire stay.");
        var booking=new Booking(guest,room,key,in.checkIn(),in.checkOut(),in.rooms(),in.guests());
        for(var night:nights){night.reserve(in.rooms());booking.addNight(night.getStayDate(),night.getNightlyPrice());}
        if(in.expectedTotal()!=null && booking.getTotal().compareTo(in.expectedTotal())!=0)
            throw conflict("The price changed. Refresh availability and confirm the new total.");
        if(payments.enabled){
            long minor=com.hotel.hotel_booking.payment.PaymentSettings.minor(booking.getTotal(),booking.getCurrencyCode());
            if(minor==0)booking.freeConfirmed();else {payments.requireReady();booking.hold(Instant.now().plusSeconds(35*60));}
        }
        bookings.saveAndFlush(booking);
        if(booking.getStatus()==BookingStatus.CONFIRMED)notifications.booking(booking,false);
        return Detail.from(booking);
    }
    public PageResponse<Summary> list(String email,int page,int size){
        return PageResponse.from(bookings.findByGuestId(management.actor(email,Role.USER).getId(),page(page,size)).map(Summary::from));
    }
    public Detail get(String email,Long id){
        return Detail.from(bookings.findByIdAndGuestId(id,management.actor(email,Role.USER).getId()).orElseThrow(HotelManagementService::missing));
    }
    @Transactional public Detail cancel(String email,Long id){
        User guest=lockedGuest(email);
        Long roomId=bookings.findOwnedRoomId(id,guest.getId()).orElseThrow(HotelManagementService::missing);
        lockedRoom(roomId);
        Booking booking=bookings.findOwnedForUpdate(id,guest.getId()).orElseThrow(HotelManagementService::missing);
        if(booking.getStatus()==BookingStatus.CANCELLED || booking.getStatus()==BookingStatus.EXPIRED) return Detail.from(booking);
        if(booking.getStatus()==BookingStatus.PENDING_PAYMENT)throw conflict("The checkout must be closed before releasing this reservation.");
        if(booking.getPaymentStatus().equals("PAID")||booking.getPaymentStatus().equals("REVIEW_REQUIRED")||booking.getPaymentStatus().startsWith("REFUND"))throw conflict("Paid reservations must use the cancellation and refund request.");
        if(!LocalDate.now(ZoneId.of(booking.getHotelTimeZone())).isBefore(booking.getCheckIn()))
            throw conflict("Online cancellation is available only before the hotel's check-in date.");
        var nights=inventory.findNightsForUpdate(roomId,booking.getCheckIn(),booking.getCheckOut());
        if(nights.size()!=validateRange(booking.getCheckIn(),booking.getCheckOut())) throw conflict("Cannot cancel: inventory needs reconciliation. Contact support.");
        for(var night:nights) if(night.getBookedRooms()<booking.getQuantity()) throw conflict("Cannot cancel: inventory needs reconciliation. Contact support.");
        for(var night:nights) night.release(booking.getQuantity());
        booking.cancel();
        notifications.booking(booking,true);
        bookings.flush();
        return Detail.from(booking);
    }
    public PageResponse<HostBooking> hostList(String email,Long hotelId,int page,int size){
        management.ownedHotel(hotelId,email,false);
        return PageResponse.from(bookings.findByRoomTypeHotelId(hotelId,page(page,size)).map(HostBooking::from));
    }
}
