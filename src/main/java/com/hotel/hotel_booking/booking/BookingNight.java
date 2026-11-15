package com.hotel.hotel_booking.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

@Entity
@Table(name="stay_booking_nights",uniqueConstraints=@UniqueConstraint(name="uk_stay_booking_night",columnNames={"booking_id","stay_date"}))
@Check(name="ck_stay_booking_night_price",constraints="unit_price >= 0")
public class BookingNight {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="booking_id",nullable=false,foreignKey=@ForeignKey(name="fk_stay_booking_night")) private Booking booking;
    @Column(name="stay_date",nullable=false) private LocalDate stayDate;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
    protected BookingNight() {}
    BookingNight(Booking booking,LocalDate date,BigDecimal price){this.booking=booking;this.stayDate=date;this.unitPrice=price;}
    public LocalDate getStayDate(){return stayDate;} public BigDecimal getUnitPrice(){return unitPrice;}
}
