package com.hotel.hotel_booking;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.auth.PasswordResetRepository;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingApiTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory;
    @Autowired BookingRepository bookings;
    @Autowired PasswordResetRepository resets;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    String hash;
    Session host,guest,other;
    LocalDate from=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(30);
    @BeforeAll void password(){hash=encoder.encode("SampleOnly123");}
    void clean(){jdbc.update("delete from stay_booking_nights");bookings.deleteAllInBatch();inventory.deleteAllInBatch();rooms.deleteAllInBatch();hotels.deleteAllInBatch();resets.deleteAllInBatch();users.deleteAllInBatch();}
    @BeforeEach void setup() throws Exception {
        clean();
        users.save(new User("Host","host@example.com",hash,Role.HOST));
        users.save(new User("Guest","guest@example.com",hash,Role.USER));
        users.save(new User("Other Guest","other@example.com",hash,Role.USER));
        host=login("host@example.com"); guest=login("guest@example.com"); other=login("other@example.com");
    }
    @AfterEach void teardown(){clean();}
    class Session {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body,boolean withCsrf) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
            if(body!=null) builder.header("Content-Type","application/json");
            if(withCsrf){var token=send("GET","/api/auth/csrf",null,false);builder.header("X-CSRF-TOKEN",field(token.body(),"token"));}
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String path) throws Exception{return send("GET",path,null,false);}
        HttpResponse<String> post(String path,String body) throws Exception{return send("POST",path,body,true);}
    }
    Session login(String email) throws Exception {
        var session=new Session();
        assertEquals(200,session.post("/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());
        session.get("/api/auth/csrf");return session;
    }
    static String field(String json,String name){var m=Pattern.compile("\""+name+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),json);return m.group(1);}
    static long id(String json){var m=Pattern.compile("\"id\":(\\d+)").matcher(json);assertTrue(m.find(),json);return Long.parseLong(m.group(1));}
    long createHotel() throws Exception {
        var result=host.post("/api/host/hotels","{\"name\":\"STAY API Hotel\",\"slug\":\"stay-api-hotel\",\"description\":\"Test\",\"address\":\"1 Test Road\",\"city\":\"Kuala Lumpur\",\"countryCode\":\"MY\",\"currencyCode\":\"MYR\",\"timeZone\":\"Asia/Kuala_Lumpur\",\"hostId\":999999}");
        assertEquals(201,result.statusCode(),result.body());return id(result.body());
    }
    long setupRoom(long hotelId,int stock) throws Exception {
        var result=host.post("/api/host/hotels/"+hotelId+"/room-types","{\"code\":\"DELUXE\",\"name\":\"Deluxe King\",\"description\":\"Test\",\"maxGuests\":2,\"beds\":1,\"totalRooms\":"+stock+",\"basePrice\":250.00}");
        assertEquals(201,result.statusCode(),result.body());long roomId=id(result.body());
        assertEquals(200,host.post("/api/host/room-types/"+roomId+"/inventory/initialize","{\"from\":\""+from+"\",\"to\":\""+from.plusDays(2)+"\"}").statusCode());
        publishFixture(hotelId); // Review authorization is exercised by AdminApiTests.
        return roomId;
    }
    void publishFixture(long id){var hotel=hotels.findById(id).orElseThrow();hotel.publish();hotels.saveAndFlush(hotel);}
    String booking(long roomId,String key){return "{\"roomTypeId\":"+roomId+",\"checkIn\":\""+from+"\",\"checkOut\":\""+from.plusDays(2)+"\",\"rooms\":1,\"guests\":2,\"requestKey\":\""+key+"\",\"total\":0,\"guestId\":999999}";}
    @Test void hotelManagementOwnershipPublicationValidationAndCsrf() throws Exception {
        assertEquals(403,guest.get("/api/host/overview").statusCode());
        long hotelId=createHotel();
        assertTrue(host.get("/api/host/overview").body().contains("\"hotels\":1"));
        assertEquals(404,new Session().get("/api/hotels/"+hotelId).statusCode());
        assertEquals(403,guest.get("/api/host/hotels/"+hotelId).statusCode());
        assertEquals(403,host.send("PUT","/api/host/hotels/"+hotelId,"{}",false).statusCode());
        users.save(new User("Other Host","otherhost@example.com",hash,Role.HOST));
        var secondHost=login("otherhost@example.com");
        assertTrue(secondHost.get("/api/host/overview").body().contains("\"hotels\":0"));
        assertEquals(404,secondHost.get("/api/host/hotels/"+hotelId).statusCode());
        assertEquals(404,secondHost.post("/api/host/hotels/"+hotelId+"/room-types","{\"code\":\"X\",\"name\":\"X\",\"maxGuests\":1,\"beds\":1,\"totalRooms\":1,\"basePrice\":1}").statusCode());
        long roomId=setupRoom(hotelId,2);
        assertEquals(200,new Session().get("/api/hotels/"+hotelId).statusCode());
        assertEquals(200,new Session().get("/api/hotels?city=Kuala%20Lumpur").statusCode());
        assertEquals(400,new Session().get("/api/hotels?size=1000").statusCode());
        assertEquals(400,new Session().get("/api/hotels/"+hotelId+"/availability?checkIn=bad&checkOut=bad").statusCode());
        assertEquals(400,host.post("/api/host/hotels/"+hotelId+"/room-types","{}").statusCode());
        assertEquals(404,secondHost.send("PUT","/api/host/room-types/"+roomId+"/inventory/"+from,"{\"totalRooms\":2,\"blockedRooms\":0,\"nightlyPrice\":100,\"stopSell\":false}",true).statusCode());
    }
    @Test void bookingUsesDatabasePriceSnapshotsAndIdempotencyAndCancellation() throws Exception {
        long hotelId=createHotel(),roomId=setupRoom(hotelId,2);
        assertEquals(200,host.send("PUT","/api/host/room-types/"+roomId+"/inventory/"+from.plusDays(1),"{\"totalRooms\":2,\"blockedRooms\":0,\"nightlyPrice\":300,\"stopSell\":false}",true).statusCode());
        String key=UUID.randomUUID().toString(),body=booking(roomId,key);
        assertEquals(409,guest.post("/api/bookings",body.replace("\"total\":0", "\"expectedTotal\":0")).statusCode());
        assertEquals(0,bookings.count());
        assertEquals(0,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        var created=guest.post("/api/bookings",body);assertEquals(201,created.statusCode(),created.body());
        long bookingId=id(created.body());
        assertTrue(created.body().contains("\"total\":550.00"),created.body());
        assertTrue(created.body().contains("UNPAID"));
        assertEquals(201,guest.post("/api/bookings",body).statusCode());assertEquals(1,bookings.count());
        assertEquals(409,guest.post("/api/bookings",body.replace("\"guests\":2","\"guests\":1")).statusCode());
        assertEquals(404,other.get("/api/bookings/"+bookingId).statusCode());
        assertEquals(404,other.post("/api/bookings/"+bookingId+"/cancel",null).statusCode());
        assertEquals(403,host.post("/api/bookings",body).statusCode());
        assertEquals(200,host.get("/api/host/hotels/"+hotelId+"/bookings").statusCode());
        assertTrue(host.get("/api/host/overview").body().contains("\"confirmedBookings\":1"));
        assertEquals(200,guest.get("/api/bookings").statusCode());
        host.send("PUT","/api/host/room-types/"+roomId+"/inventory/"+from.plusDays(1),"{\"totalRooms\":2,\"blockedRooms\":0,\"nightlyPrice\":999,\"stopSell\":false}",true);
        assertTrue(guest.get("/api/bookings/"+bookingId).body().contains("\"total\":550.00"));
        assertEquals(200,guest.post("/api/bookings/"+bookingId+"/cancel",null).statusCode());
        assertEquals(200,guest.post("/api/bookings/"+bookingId+"/cancel",null).statusCode());
        assertEquals(0,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        assertEquals(BookingStatus.CANCELLED,bookings.findById(bookingId).orElseThrow().getStatus());
        assertTrue(host.get("/api/host/overview").body().contains("\"confirmedBookings\":0"));
    }
    @Test void missingOrClosedNightRollsBackWholeBooking() throws Exception {
        long hotelId=createHotel(),roomId=setupRoom(hotelId,2);
        host.send("PUT","/api/host/room-types/"+roomId+"/inventory/"+from.plusDays(1),"{\"totalRooms\":2,\"blockedRooms\":0,\"nightlyPrice\":250,\"stopSell\":true}",true);
        assertEquals(409,guest.post("/api/bookings",booking(roomId,UUID.randomUUID().toString())).statusCode());
        assertEquals(0,bookings.count());
        assertEquals(0,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        assertEquals("[]",new Session().get("/api/hotels/"+hotelId+"/availability?checkIn="+from+"&checkOut="+from.plusDays(2)+"&rooms=1&guests=2").body());
        inventory.delete(inventory.findByRoomTypeIdAndStayDate(roomId,from.plusDays(1)).orElseThrow());
        assertEquals(409,guest.post("/api/bookings",booking(roomId,UUID.randomUUID().toString())).statusCode());
        assertEquals(0,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
    }
    @Test void enforcesDateCapacityPublicationAndHostStockBoundaries() throws Exception {
        long hotelId=createHotel(),roomId=setupRoom(hotelId,2);
        String body=booking(roomId,UUID.randomUUID().toString());
        assertEquals(400,guest.post("/api/bookings",body.replace("\"guests\":2","\"guests\":3")).statusCode());
        assertEquals(400,guest.post("/api/bookings",body.replace(from.toString(),from.minusYears(2).toString())).statusCode());
        host.send("PATCH","/api/host/hotels/"+hotelId+"/status","{\"status\":\"DRAFT\"}",true);
        assertEquals(409,guest.post("/api/bookings",body).statusCode());
        publishFixture(hotelId);
        assertEquals(201,guest.post("/api/bookings",body).statusCode());
        assertEquals(400,host.send("PUT","/api/host/room-types/"+roomId+"/inventory/"+from,"{\"totalRooms\":0,\"blockedRooms\":0,\"nightlyPrice\":250,\"stopSell\":false}",true).statusCode());
        assertEquals(1,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        var today=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"));
        host.post("/api/host/room-types/"+roomId+"/inventory/initialize","{\"from\":\""+today+"\",\"to\":\""+today.plusDays(1)+"\"}");
        String sameDay=body.replace(from.toString(),today.toString()).replace(from.plusDays(2).toString(),today.plusDays(1).toString()).replace(field(body,"requestKey"),UUID.randomUUID().toString());
        var created=guest.post("/api/bookings",sameDay);assertEquals(201,created.statusCode(),created.body());
        assertEquals(409,guest.post("/api/bookings/"+id(created.body())+"/cancel",null).statusCode());
    }
    @Test void simultaneousGuestsCannotBookTheLastRoomTwice() throws Exception {
        long hotelId=createHotel(),roomId=setupRoom(hotelId,1);
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=executor.submit(()->{start.await();return guest.post("/api/bookings",booking(roomId,UUID.randomUUID().toString())).statusCode();});
            var second=executor.submit(()->{start.await();return other.post("/api/bookings",booking(roomId,UUID.randomUUID().toString())).statusCode();});
            start.countDown();var codes=new ArrayList<>(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));Collections.sort(codes);
            assertEquals(List.of(201,409),codes);
            assertEquals(1,bookings.count());
            assertEquals(1,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        } finally {executor.shutdownNow();}
    }
    @Test void concurrentRetriesProduceOneBookingAndOneStockDeduction() throws Exception {
        long hotelId=createHotel(),roomId=setupRoom(hotelId,2);String body=booking(roomId,UUID.randomUUID().toString());
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=executor.submit(()->{start.await();return guest.post("/api/bookings",body);});
            var second=executor.submit(()->{start.await();return guest.post("/api/bookings",body);});start.countDown();
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            assertEquals(201,a.statusCode(),a.body());assertEquals(201,b.statusCode(),b.body());assertEquals(id(a.body()),id(b.body()));
            assertEquals(1,bookings.count());assertEquals(1,inventory.findByRoomTypeIdAndStayDate(roomId,from).orElseThrow().getBookedRooms());
        } finally {executor.shutdownNow();}
    }
}
