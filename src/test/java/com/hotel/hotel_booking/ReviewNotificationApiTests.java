package com.hotel.hotel_booking;
import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.review.*;
import com.hotel.hotel_booking.notification.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:review_notifications;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewNotificationApiTests {
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired HotelRepository hotels; @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory; @Autowired InventoryService stock; @Autowired BookingRepository bookings;
    @Autowired ReviewRepository reviews; @Autowired NotificationRepository notifications; @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate jdbc;
    Session guest,host,other,otherHost;long hotel,room;String hash;LocalDate today=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")),from=today.plusDays(30);
    void clean(){reviews.deleteAllInBatch();notifications.deleteAllInBatch();jdbc.update("delete from stay_booking_nights");bookings.deleteAllInBatch();inventory.deleteAllInBatch();rooms.deleteAllInBatch();hotels.deleteAllInBatch();users.deleteAllInBatch();}
    @BeforeAll void password(){hash=encoder.encode("SampleOnly123");}
    @BeforeEach void setup()throws Exception{
        clean();var owner=users.save(new User("Hotel Owner","reviewhost@example.com",hash,Role.HOST));users.save(new User("Guest Alex","reviewguest@example.com",hash,Role.USER));users.save(new User("Other Guest","reviewother@example.com",hash,Role.USER));users.save(new User("Other Host","reviewotherhost@example.com",hash,Role.HOST));
        var h=new Hotel(owner,"Review Hotel","review-hotel","Hotel description","1 Road","Raub","MY","MYR","Asia/Kuala_Lumpur");h.publish();h=hotels.save(h);hotel=h.getId();
        room=rooms.save(new RoomType(h,"KING","King room","Description",2,1,3,new java.math.BigDecimal("100.00"))).getId();stock.initializeNights(room,owner.getId(),from,from.plusDays(2));
        guest=login("reviewguest@example.com");host=login("reviewhost@example.com");other=login("reviewother@example.com");otherHost=login("reviewotherhost@example.com");
    }
    @AfterEach void teardown(){clean();}
    class Session {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body)throws Exception{return send(method,path,body,true);}
        HttpResponse<String> send(String method,String path,String body,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
            if(csrf&&!method.equals("GET"))request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null,false).body(),"token"));
            if(body!=null)request.header("Content-Type","application/json");return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{var s=new Session();assertEquals(200,s.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());return s;}
    static String field(String json,String name){var m=Pattern.compile("\""+name+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),json);return m.group(1);}
    static long id(String json){var m=Pattern.compile("\"id\":(\\d+)").matcher(json);assertTrue(m.find(),json);return Long.parseLong(m.group(1));}
    String request(String key){return "{\"roomTypeId\":"+room+",\"checkIn\":\""+from+"\",\"checkOut\":\""+from.plusDays(2)+"\",\"rooms\":1,\"guests\":2,\"requestKey\":\""+key+"\"}";}
    long book()throws Exception{var response=guest.send("POST","/api/bookings",request(UUID.randomUUID().toString()));assertEquals(201,response.statusCode(),response.body());return id(response.body());}
    void past(long booking){jdbc.update("update stay_bookings set check_in=?,check_out=? where id=?",today.minusDays(3),today.minusDays(1),booking);}
    String review(long b){return "/api/bookings/"+b+"/review";}String publicReviews(){return "/api/hotels/"+hotel+"/reviews";}
    @Test void bookingEventsAreAtomicIdempotentAndReadStateIsPrivate()throws Exception{
        String body=request(UUID.randomUUID().toString());long b=id(guest.send("POST","/api/bookings",body).body());assertEquals(2,notifications.count());guest.send("POST","/api/bookings",body);assertEquals(2,notifications.count());
        assertEquals(401,new Session().send("GET","/api/account/notifications",null).statusCode());
        assertTrue(guest.send("GET","/api/account/notifications/unread-count",null).body().contains("\"unreadCount\":1"));assertTrue(other.send("GET","/api/account/notifications",null).body().contains("\"items\":[]"));
        long notice=id(guest.send("GET","/api/account/notifications",null).body());assertEquals(404,other.send("POST","/api/account/notifications/"+notice+"/read",null).statusCode());assertEquals(403,guest.send("POST","/api/account/notifications/"+notice+"/read",null,false).statusCode());
        assertEquals(204,guest.send("POST","/api/account/notifications/"+notice+"/read",null).statusCode());assertEquals(204,guest.send("POST","/api/account/notifications/"+notice+"/read",null).statusCode());
        assertEquals(200,guest.send("POST","/api/bookings/"+b+"/cancel",null).statusCode());guest.send("POST","/api/bookings/"+b+"/cancel",null);assertEquals(4,notifications.count());
        guest.send("POST","/api/account/notifications/read-all",null);assertTrue(guest.send("GET","/api/account/notifications/unread-count",null).body().contains("\"unreadCount\":0"));assertTrue(host.send("GET","/api/account/notifications/unread-count",null).body().contains("\"unreadCount\":2"));
        assertEquals(400,guest.send("GET","/api/account/notifications?size=1000",null).statusCode());
    }
    @Test void reviewEligibilityPrivacyEditsAggregatesAndHostReplies()throws Exception{
        long b=book();assertTrue(guest.send("GET",review(b),null).body().contains("\"eligible\":false"));assertEquals(409,guest.send("PUT",review(b),"{\"rating\":5,\"comment\":\"Too early\"}").statusCode());assertEquals(0,reviews.count());past(b);
        assertEquals(404,other.send("GET",review(b),null).statusCode());assertEquals(403,host.send("PUT",review(b),"{\"rating\":5,\"comment\":\"Not a guest\"}").statusCode());assertEquals(400,guest.send("PUT",review(b),"{\"rating\":6,\"comment\":\"Bad rating\"}").statusCode());assertEquals(403,guest.send("PUT",review(b),"{\"rating\":4,\"comment\":\"Nice\"}",false).statusCode());
        var created=guest.send("PUT",review(b),"{\"rating\":4,\"comment\":\"Quiet and comfortable\"}");assertEquals(200,created.statusCode(),created.body());long r=id(created.body());assertEquals(3,notifications.count());
        guest.send("PUT",review(b),"{\"rating\":5,\"comment\":\"Updated review\"}");assertEquals(1,reviews.count());assertEquals(3,notifications.count());
        var publicPage=new Session().send("GET",publicReviews(),null);assertEquals(200,publicPage.statusCode());assertTrue(publicPage.body().contains("\"averageRating\":5.0"));assertTrue(publicPage.body().contains("Updated review"));assertFalse(publicPage.body().contains("reviewguest@example.com"));assertFalse(publicPage.body().contains("bookingId"));
        var highlights=new Session().send("GET","/api/hotels/review-highlights",null);assertEquals(200,highlights.statusCode(),highlights.body());assertTrue(highlights.body().contains("Updated review"));assertFalse(highlights.body().contains("reviewguest@example.com"));assertFalse(highlights.body().contains("bookingId"));
        String reply="/api/host/hotels/"+hotel+"/reviews/"+r+"/reply";assertEquals(404,otherHost.send("PUT",reply,"{\"reply\":\"Unauthorized\"}").statusCode());assertEquals(200,host.send("PUT",reply,"{\"reply\":\"Thank you for staying\"}").statusCode());assertEquals(4,notifications.count());host.send("PUT",reply,"{\"reply\":\"Updated response\"}");assertEquals(4,notifications.count());assertTrue(new Session().send("GET",publicReviews(),null).body().contains("Updated response"));
        host.send("PATCH","/api/host/hotels/"+hotel+"/status","{\"status\":\"DRAFT\"}");assertEquals(404,new Session().send("GET",publicReviews(),null).statusCode());assertEquals(200,host.send("GET","/api/host/hotels/"+hotel+"/reviews",null).statusCode());assertEquals("[]",new Session().send("GET","/api/hotels/review-highlights",null).body());
    }
    @Test void cancelledBookingsAndFailedBookingsCreateNoReviewsOrPhantomEvents()throws Exception{
        String body=request(UUID.randomUUID().toString()).replace("}",",\"expectedTotal\":0}");assertEquals(409,guest.send("POST","/api/bookings",body).statusCode());assertEquals(0,notifications.count());assertEquals(0,bookings.count());
        long b=book();guest.send("POST","/api/bookings/"+b+"/cancel",null);past(b);assertEquals(409,guest.send("PUT",review(b),"{\"rating\":5,\"comment\":\"Cancelled\"}").statusCode());assertEquals(0,reviews.count());assertEquals(4,notifications.count());
    }
    @Test void concurrentReviewRetriesProduceOneReviewAndOneHostEvent()throws Exception{
        long b=book();past(b);var second=login("reviewguest@example.com");String body="{\"rating\":4,\"comment\":\"Good stay\"}";
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->guest.send("PUT",review(b),body));var c=pool.submit(()->second.send("PUT",review(b),body));assertEquals(200,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(200,c.get(25,TimeUnit.SECONDS).statusCode());}
        assertEquals(1,reviews.count());assertEquals(3,notifications.count());
    }
}
