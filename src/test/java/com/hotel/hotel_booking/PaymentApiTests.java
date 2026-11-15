package com.hotel.hotel_booking;

import com.hotel.hotel_booking.payment.*;
import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import com.hotel.hotel_booking.user.Role;
import com.hotel.hotel_booking.notification.NotificationRepository;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;import java.util.regex.Pattern;import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:payment;MODE=MySQL;DB_CLOSE_DELAY=-1","app.payments.enabled=true","app.payments.stripe-secret=sk_test_fixture_only","app.payments.webhook-secret=whsec_fixture_only","app.payments.scheduling-enabled=false"})
@ActiveProfiles("test") @Import(PaymentApiTests.GatewayConfig.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentApiTests {
    @TestConfiguration static class GatewayConfig {@Bean @Primary FakeStripe fakeStripe(){return new FakeStripe();}}
    static class FakeStripe implements StripeGateway {
        final Map<String,Session> sessions=new ConcurrentHashMap<>();final Map<String,String> keys=new ConcurrentHashMap<>();final AtomicInteger created=new AtomicInteger();
        volatile boolean unavailable,ambiguous,payOnExpire,expireUnavailable;
        void reset(){sessions.clear();keys.clear();created.set(0);unavailable=false;ambiguous=false;payOnExpire=false;expireUnavailable=false;}
        ResponseStatusException down(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Test gateway unavailable; hold retained.");}
        public Session create(Request r){if(unavailable)throw down();String id=keys.computeIfAbsent(r.attemptKey(),key->{created.incrementAndGet();String session="cs_test_"+key.replace("-","");sessions.put(session,new Session(session,"https://checkout.stripe.com/c/pay/"+session,"open","unpaid",r.amount(),r.currency().toLowerCase(Locale.ROOT),r.bookingId().toString(),key,null,r.expiresAt(),false,"STAY"));return session;});if(ambiguous){ambiguous=false;throw down();}return sessions.get(id);}
        public Session retrieve(String id){if(unavailable)throw down();return Objects.requireNonNull(sessions.get(id));}
        public Session expire(String id){if(expireUnavailable)throw down();if(payOnExpire){pay(id);return retrieve(id);}var old=retrieve(id);if(old.status().equals("open"))sessions.put(id,new Session(old.id(),null,"expired","unpaid",old.amount(),old.currency(),old.reference(),old.attemptKey(),null,old.expiresAt(),false,"STAY"));return retrieve(id);}
        void pay(String id){var old=sessions.get(id);sessions.put(id,new Session(old.id(),null,"complete","paid",old.amount(),old.currency(),old.reference(),old.attemptKey(),"pi_fixture_"+id,old.expiresAt(),false,"STAY"));}
        String first(){return sessions.keySet().iterator().next();}
    }
    @LocalServerPort int port;
    @Autowired FakeStripe stripe;@Autowired PaymentService payments;@Autowired PaymentAttemptRepository attempts;@Autowired PaymentEventRepository events;
    @Autowired UserRepository users;@Autowired HotelRepository hotels;@Autowired RoomTypeRepository rooms;@Autowired RoomInventoryRepository inventory;@Autowired InventoryService stock;@Autowired BookingRepository bookings;@Autowired NotificationRepository notifications;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
    Session guest,other,host;long hotel,room;String hash;LocalDate from=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(30);
    void clean(){events.deleteAllInBatch();attempts.deleteAllInBatch();notifications.deleteAllInBatch();jdbc.update("delete from stay_booking_nights");bookings.deleteAllInBatch();inventory.deleteAllInBatch();rooms.deleteAllInBatch();hotels.deleteAllInBatch();users.deleteAllInBatch();}
    @BeforeAll void password(){hash=encoder.encode("SampleOnly123");}
    @BeforeEach void setup()throws Exception{
        clean();stripe.reset();var owner=users.save(new User("Host","paymenthost@example.com",hash,Role.HOST));users.save(new User("Guest","paymentguest@example.com",hash,Role.USER));users.save(new User("Other","paymentother@example.com",hash,Role.USER));
        var h=new Hotel(owner,"Payment Hotel","payment-hotel","Test","1 Road","Raub","MY","MYR","Asia/Kuala_Lumpur");h.publish();h=hotels.save(h);hotel=h.getId();room=rooms.save(new RoomType(h,"KING","King","Test",2,1,3,new java.math.BigDecimal("100"))).getId();stock.initializeNights(room,owner.getId(),from,from.plusDays(2));guest=login("paymentguest@example.com");other=login("paymentother@example.com");host=login("paymenthost@example.com");
    }
    @AfterEach void teardown(){clean();}
    class Session {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body)throws Exception{return send(method,path,body,true);}
        HttpResponse<String> send(String method,String path,String body,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(25));if(csrf&&!method.equals("GET"))request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null,false).body(),"token"));if(body!=null)request.header("Content-Type","application/json");return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{var s=new Session();assertEquals(200,s.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());return s;}
    static String field(String json,String name){var m=Pattern.compile("\""+name+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),json);return m.group(1);}
    static long id(String json){var m=Pattern.compile("\"id\":(\\d+)").matcher(json);assertTrue(m.find(),json);return Long.parseLong(m.group(1));}
    String create(String key){return "{\"roomTypeId\":"+room+",\"checkIn\":\""+from+"\",\"checkOut\":\""+from.plusDays(2)+"\",\"rooms\":1,\"guests\":2,\"requestKey\":\""+key+"\"}";}
    long book()throws Exception{var r=guest.send("POST","/api/bookings",create(UUID.randomUUID().toString()));assertEquals(201,r.statusCode(),r.body());return id(r.body());}
    String path(long b){return "/api/bookings/"+b;}
    void checkout(long b)throws Exception{var r=guest.send("POST",path(b)+"/checkout",null);assertEquals(200,r.statusCode(),r.body());}
    int reserved(){return jdbc.queryForObject("select sum(booked_rooms) from stay_room_inventory",Integer.class);}
    void due(long b){jdbc.update("update stay_bookings set expires_at=? where id=?",Instant.now().minusSeconds(60),b);}
    String event(String eventId,String session){return "{\"id\":\""+eventId+"\",\"type\":\"checkout.session.completed\",\"livemode\":false,\"data\":{\"object\":{\"id\":\""+session+"\",\"object\":\"checkout.session\"}}}";}
    String signature(String body,long time)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("whsec_fixture_only".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "t="+time+",v1="+HexFormat.of().formatHex(mac.doFinal((time+"."+body).getBytes(StandardCharsets.UTF_8)));}
    HttpResponse<String> webhook(String body,String signature)throws Exception{var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/payments/webhook")).header("Content-Type","application/json").header("Stripe-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(body)).build();return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> webhook(String id)throws Exception{String body=event(id,stripe.first());return webhook(body,signature(body,Instant.now().getEpochSecond()));}
    @Test void pendingBookingCheckoutIsOwnedAndIdempotent()throws Exception{
        String body=create(UUID.randomUUID().toString());long b=id(guest.send("POST","/api/bookings",body).body());guest.send("POST","/api/bookings",body);assertEquals(1,bookings.count());assertEquals(2,reserved());assertEquals(0,notifications.count());
        assertTrue(guest.send("GET",path(b),null).body().contains("PENDING_PAYMENT"));assertEquals(404,other.send("POST",path(b)+"/checkout",null).statusCode());assertEquals(403,host.send("POST",path(b)+"/checkout",null).statusCode());assertEquals(403,guest.send("POST",path(b)+"/checkout",null,false).statusCode());
        checkout(b);checkout(b);assertEquals(1,stripe.created.get());assertEquals(1,attempts.count());assertEquals(20000,stripe.sessions.get(stripe.first()).amount());assertEquals(404,other.send("GET",path(b)+"/payment",null).statusCode());
    }
    @Test void signedWebhookConfirmsExactlyOnceAndNeverReleasesPaidInventory()throws Exception{
        long b=book();checkout(b);stripe.pay(stripe.first());assertEquals(204,webhook("evt_paid").statusCode());assertEquals(204,webhook("evt_paid").statusCode());assertEquals(204,webhook("evt_duplicate_object").statusCode());
        assertEquals("PAID",bookings.findById(b).orElseThrow().getPaymentStatus());assertEquals(BookingStatus.CONFIRMED,bookings.findById(b).orElseThrow().getStatus());assertEquals(2,notifications.count());assertEquals(2,events.count());assertEquals(2,reserved());assertEquals(409,guest.send("POST",path(b)+"/cancel",null).statusCode());assertEquals(2,reserved());
    }
    @Test void invalidSignaturesAndExpiredTimestampsCannotConfirmPayment()throws Exception{
        long b=book();checkout(b);stripe.pay(stripe.first());String body=event("evt_bad",stripe.first());assertEquals(400,webhook(body,"t=1,v1="+"0".repeat(64)).statusCode());assertEquals(400,webhook(body,signature(body,Instant.now().minusSeconds(301).getEpochSecond())).statusCode());assertEquals(400,webhook(body+" ",signature(body,Instant.now().getEpochSecond())).statusCode());assertEquals(BookingStatus.PENDING_PAYMENT,bookings.findById(b).orElseThrow().getStatus());assertEquals(0,events.count());
    }
    @Test void noCheckoutHoldExpiresAndReleasesOnlyOnce()throws Exception{
        long b=book();due(b);payments.reconcile(b,false);payments.reconcile(b,false);assertEquals(BookingStatus.EXPIRED,bookings.findById(b).orElseThrow().getStatus());assertEquals(0,reserved());assertEquals(1,notifications.count());assertEquals(0,stripe.created.get());
    }
    @Test void openCheckoutMustCloseBeforeExpiryReleasesRooms()throws Exception{
        long b=book();checkout(b);due(b);stripe.expireUnavailable=true;assertThrows(ResponseStatusException.class,()->payments.reconcile(b,false));assertEquals(2,reserved());assertEquals(BookingStatus.PENDING_PAYMENT,bookings.findById(b).orElseThrow().getStatus());stripe.expireUnavailable=false;payments.reconcile(b,false);assertEquals(0,reserved());assertEquals(BookingStatus.EXPIRED,bookings.findById(b).orElseThrow().getStatus());
    }
    @Test void paymentWinningCancellationRaceConfirmsWithoutReleasingStock()throws Exception{
        long b=book();checkout(b);stripe.payOnExpire=true;assertEquals(409,guest.send("POST",path(b)+"/cancel",null).statusCode());assertEquals("PAID",bookings.findById(b).orElseThrow().getPaymentStatus());assertEquals(BookingStatus.CONFIRMED,bookings.findById(b).orElseThrow().getStatus());assertEquals(2,reserved());
    }
    @Test void ambiguousCreateIsRecoveredWithSameKeyAndKeepsInventoryUntilReconciled()throws Exception{
        long b=book();stripe.ambiguous=true;assertEquals(503,guest.send("POST",path(b)+"/checkout",null).statusCode());assertEquals(1,stripe.created.get());due(b);stripe.unavailable=true;assertThrows(ResponseStatusException.class,()->payments.reconcile(b,false));assertEquals(2,reserved());stripe.unavailable=false;payments.reconcile(b,false);assertEquals(1,stripe.created.get());assertEquals(0,reserved());assertEquals(BookingStatus.EXPIRED,bookings.findById(b).orElseThrow().getStatus());
    }
    @Test void amountMismatchIsRejectedBeforeEventOrPaidStateIsSaved()throws Exception{
        long b=book();checkout(b);stripe.pay(stripe.first());var s=stripe.sessions.get(stripe.first());stripe.sessions.put(s.id(),new StripeGateway.Session(s.id(),s.url(),s.status(),s.paymentStatus(),1,s.currency(),s.reference(),s.attemptKey(),s.paymentIntent(),s.expiresAt(),false,s.app()));assertEquals(503,webhook("evt_mismatch").statusCode());assertEquals(BookingStatus.PENDING_PAYMENT,bookings.findById(b).orElseThrow().getStatus());assertEquals(0,events.count());assertEquals(2,reserved());stripe.sessions.put(s.id(),s);assertEquals(204,webhook("evt_mismatch").statusCode());
    }
    @Test void concurrentCheckoutAndWebhookRetriesAreDeduplicated()throws Exception{
        long b=book();var second=login("paymentguest@example.com");try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->guest.send("POST",path(b)+"/checkout",null));var c=pool.submit(()->second.send("POST",path(b)+"/checkout",null));assertEquals(200,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(200,c.get(25,TimeUnit.SECONDS).statusCode());}assertEquals(1,stripe.created.get());stripe.pay(stripe.first());try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->webhook("evt_concurrent"));var c=pool.submit(()->webhook("evt_concurrent"));assertEquals(204,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(204,c.get(25,TimeUnit.SECONDS).statusCode());}assertEquals(1,events.count());assertEquals(2,notifications.count());
    }
    @Test void unpaidCancellationClosesCheckoutAndReleasesOnce()throws Exception{
        long first=book();assertEquals(200,guest.send("POST",path(first)+"/cancel",null).statusCode());assertEquals(0,reserved());
        long second=book();checkout(second);assertEquals(200,guest.send("POST",path(second)+"/cancel",null).statusCode());assertEquals(BookingStatus.CANCELLED,bookings.findById(second).orElseThrow().getStatus());assertEquals(0,reserved());assertEquals(200,guest.send("POST",path(second)+"/cancel",null).statusCode());assertEquals(0,reserved());
    }
    @Test void freeBookingsNeedNoCheckoutAndUnsupportedCurrenciesRollBack()throws Exception{
        jdbc.update("update stay_room_inventory set nightly_price=0");long b=book();assertEquals("NOT_REQUIRED",bookings.findById(b).orElseThrow().getPaymentStatus());assertEquals(BookingStatus.CONFIRMED,bookings.findById(b).orElseThrow().getStatus());assertEquals(409,guest.send("POST",path(b)+"/checkout",null).statusCode());assertEquals(0,attempts.count());
        jdbc.update("update stay_hotels set currency_code='CHF' where id=?",hotel);assertEquals(400,guest.send("POST","/api/bookings",create(UUID.randomUUID().toString())).statusCode());assertEquals(1,bookings.count());assertEquals(2,reserved());
    }
}
