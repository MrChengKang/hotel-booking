package com.hotel.hotel_booking.review;
import com.hotel.hotel_booking.booking.Booking;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Check;

@Entity @Table(name="stay_hotel_reviews",uniqueConstraints=@UniqueConstraint(name="uk_stay_review_booking",columnNames="booking_id"))
@Check(name="ck_stay_review_rating",constraints="rating between 1 and 5")
public class HotelReview {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="booking_id",nullable=false) private Booking booking;
    @Column(nullable=false) private int rating;
    @Column(nullable=false,length=2000) private String comment;
    @Column(length=1000) private String hostReply;
    @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    private Instant repliedAt;
    protected HotelReview() {}
    public HotelReview(Booking booking,int rating,String comment){this.booking=booking;edit(rating,comment);}
    public void edit(int rating,String comment){this.rating=rating;this.comment=comment.strip();updatedAt=Instant.now();}
    public void reply(String text){hostReply=text.strip();repliedAt=Instant.now();}
    public Long getId(){return id;} public Booking getBooking(){return booking;} public int getRating(){return rating;} public String getComment(){return comment;} public String getHostReply(){return hostReply;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getRepliedAt(){return repliedAt;}
}
