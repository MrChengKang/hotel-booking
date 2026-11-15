package com.hotel.hotel_booking;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.notification.NotificationRepository;
import com.hotel.hotel_booking.payment.*;
import com.hotel.hotel_booking.user.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Admin reporting reads real HTTP endpoints against disposable H2 fixtures only. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:adminorders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.payments.enabled=false", "app.payments.scheduling-enabled=false", "app.admin.bootstrap.enabled=false"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminBookingApiTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory;
    @Autowired BookingRepository bookings;
    @Autowired PaymentAttemptRepository payments;
    @Autowired RefundAttemptRepository refunds;
    @Autowired PaymentEventRepository events;
    @Autowired NotificationRepository notifications;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;

    final LocalDate checkIn=LocalDate.of(2032,4,10);
    String hash;
    User guestUser, otherGuest, literalGuest, owner, otherOwner;
    Session admin, guest, host, anonymous;
    TransactionTemplate tx;

    record Fixture(long id,long hotelId,long roomId,String reference) {}

    @BeforeAll void initialize(){
        hash=encoder.encode("SampleOnly123");
        tx=new TransactionTemplate(transactionManager);
    }

    @BeforeEach void setup()throws Exception{
        clean();
        users.save(new User("Reporting Admin","admin-orders@example.com",hash,Role.ADMIN));
        owner=users.save(new User("Harbor Owner","owner-orders@example.com",hash,Role.HOST));
        otherOwner=users.save(new User("Tokyo Owner","other-owner-orders@example.com",hash,Role.HOST));
        guestUser=users.save(new User("Reporting Traveller","traveller-orders@example.com",hash,Role.USER));
        otherGuest=users.save(new User("Another Traveller","another-orders@example.com",hash,Role.USER));
        literalGuest=users.save(new User("Literal Traveller","literal%_guest@example.com",hash,Role.USER));
        admin=login("admin-orders@example.com");
        guest=login(guestUser.getEmail());
        host=login(owner.getEmail());
        anonymous=new Session();
    }

    @AfterEach void cleanup(){clean();}

    void clean(){
        notifications.deleteAllInBatch();
        events.deleteAllInBatch();
        refunds.deleteAllInBatch();
        payments.deleteAllInBatch();
        jdbc.update("delete from stay_favorite_hotels");
        jdbc.update("delete from stay_booking_nights");
        bookings.deleteAllInBatch();
        inventory.deleteAllInBatch();
        rooms.deleteAllInBatch();
        hotels.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    class Session {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path)throws Exception{return send("GET",path,null);}
        HttpResponse<String> send(String method,String path,String body)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(25));
            if(!method.equals("GET"))request.header("X-CSRF-TOKEN",tree(get("/api/auth/csrf")).path("token").asText());
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }

    Session login(String email)throws Exception{
        var session=new Session();
        var response=session.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}");
        assertEquals(200,response.statusCode(),response.body());
        return session;
    }

    JsonNode tree(HttpResponse<String> response){return json.readTree(response.body());}
    JsonNode ok(String path)throws Exception{
        var response=admin.get(path);
        assertEquals(200,response.statusCode(),response.body());
        return tree(response);
    }
    String path(Fixture fixture){return "/api/admin/bookings/"+fixture.id();}
    String encoded(String value){return URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    int count(String path)throws Exception{return ok(path).path("totalElements").asInt();}

    Fixture booking(User traveller,User host,String hotelName,String currency,LocalDate date,boolean paid){
        return tx.execute(ignored->{
            boolean yen=currency.equals("JPY");
            var price=new BigDecimal(yen?"12000.00":"149.25");
            var hotel=hotels.saveAndFlush(new Hotel(host,hotelName,"orders-"+UUID.randomUUID(),"Hotel snapshot used by reporting fixtures",
                "18 Reporting Road",yen?"Tokyo":"Raub",yen?"JP":"MY",currency,yen?"Asia/Tokyo":"Asia/Kuala_Lumpur"));
            hotel.publish();
            var room=rooms.saveAndFlush(new RoomType(hotel,"KING","Reporting King","A fixture room",4,2,10,price));
            int quantity=yen?1:2;
            var booking=new Booking(traveller,room,UUID.randomUUID().toString(),date,date.plusDays(2),quantity,2);
            booking.addNight(date,price);
            booking.addNight(date.plusDays(1),price);
            if(paid)booking.paymentConfirmed();else booking.hold(Instant.now().plusSeconds(1800));
            bookings.saveAndFlush(booking);
            for(int i=0;i<2;i++){
                var night=new RoomInventory(room,date.plusDays(i),10,price);
                night.reserve(quantity);
                inventory.save(night);
            }
            if(paid){
                var attempt=new PaymentAttempt(booking,Instant.now().plusSeconds(1800));
                attempt.paid(providerSession(attempt,booking,true));
                payments.saveAndFlush(attempt);
            }
            return new Fixture(booking.getId(),hotel.getId(),room.getId(),booking.getReference());
        });
    }

    StripeGateway.Session providerSession(PaymentAttempt attempt,Booking booking,boolean paid){
        return new StripeGateway.Session("cs_test_reporting_"+booking.getId(),"https://checkout.stripe.com/c/pay/private_fixture_"+booking.getId(),
            paid?"complete":"open",paid?"paid":"unpaid",attempt.getAmountMinor(),booking.getCurrencyCode().toLowerCase(Locale.ROOT),
            booking.getId().toString(),attempt.getAttemptKey(),paid?"pi_reporting_"+booking.getId():null,attempt.getRequestedExpiresAt(),false,"STAY");
    }

    void openPayment(Fixture fixture){
        tx.executeWithoutResult(ignored->{
            var booking=bookings.findById(fixture.id()).orElseThrow();
            var attempt=new PaymentAttempt(booking,Instant.now().plusSeconds(1800));
            attempt.attach(providerSession(attempt,booking,false));
            payments.saveAndFlush(attempt);
        });
    }

    long refund(Fixture fixture,String status){
        return tx.execute(ignored->{
            var booking=bookings.findById(fixture.id()).orElseThrow();
            var payment=payments.findByBookingId(booking.getId()).orElseThrow();
            var refund=new RefundAttempt(booking,payment.getPaymentIntentId(),payment.getAmountMinor());
            refund.update(status.equals("INITIALIZING")?null:"re_reporting_"+booking.getId(),status);
            if(status.equals("SUCCEEDED")){
                booking.refunded();
                inventory.findNightsForUpdate(booking.getRoomType().getId(),booking.getCheckIn(),booking.getCheckOut()).forEach(night->night.release(booking.getQuantity()));
            }else if(status.equals("FAILED"))booking.refundFailed();
            else booking.refundPending();
            return refunds.saveAndFlush(refund).getId();
        });
    }

    @Test void allReportingEndpointsRequireAdminRole()throws Exception{
        var fixture=booking(guestUser,owner,"Harbor House","MYR",checkIn,true);
        refund(fixture,"FAILED");
        for(String route:List.of("/api/admin/bookings",path(fixture),"/api/admin/refunds")){
            assertEquals(401,anonymous.get(route).statusCode(),route);
            assertEquals(403,guest.get(route).statusCode(),route);
            assertEquals(403,host.get(route).statusCode(),route);
            assertEquals(200,admin.get(route).statusCode(),route);
        }
        assertEquals(404,admin.get("/api/admin/bookings/999999999").statusCode());
    }

    @Test void storedRoleIsRecheckedAfterAnAdminSessionWasCreated()throws Exception{
        var fixture=booking(guestUser,owner,"Harbor House","MYR",checkIn,true);
        refund(fixture,"REVIEW_REQUIRED");
        assertEquals(200,admin.get(path(fixture)).statusCode());
        jdbc.update("update stay_users set role='USER' where email=?","admin-orders@example.com");
        for(String route:List.of("/api/admin/bookings",path(fixture),"/api/admin/refunds")){
            assertEquals(403,admin.get(route).statusCode(),route);
        }
    }

    @Test void bookingsCanBeFilteredAcrossHostsBySnapshotHotelEmailReferenceAndInclusiveDates()throws Exception{
        var harbor=booking(guestUser,owner,"Harbor House","MYR",checkIn,true);
        var tokyo=booking(otherGuest,otherOwner,"Tokyo Garden","JPY",checkIn.plusDays(3),false);
        assertEquals(2,count("/api/admin/bookings"));
        assertEquals(1,count("/api/admin/bookings?search="+encoded(guestUser.getEmail().toUpperCase(Locale.ROOT))));
        assertEquals(1,count("/api/admin/bookings?search="+harbor.reference().substring(0,12)));
        var filtered=ok("/api/admin/bookings?hotel=HARBOR&hotelId="+harbor.hotelId()+"&status=CONFIRMED&paymentStatus=PAID&checkInFrom="+checkIn+"&checkInTo="+checkIn+"&search=traveller-orders");
        assertEquals(1,filtered.path("totalElements").asInt());
        assertEquals(harbor.id(),filtered.path("items").get(0).path("booking").path("id").asLong());
        assertEquals(0,count("/api/admin/bookings?hotelId="+tokyo.hotelId()+"&paymentStatus=PAID"));
        assertEquals(0,count("/api/admin/bookings?checkInTo="+checkIn.minusDays(1)));
        assertEquals(1,count("/api/admin/bookings?checkInFrom="+checkIn.plusDays(1)));
        tx.executeWithoutResult(ignored->hotels.findById(harbor.hotelId()).orElseThrow().edit("Renamed Current Hotel","Updated description","Changed address","Raub"));
        assertEquals(1,count("/api/admin/bookings?hotel=Harbor"));
        assertEquals(0,count("/api/admin/bookings?hotel=Renamed"));
        var detail=ok(path(tokyo));
        assertEquals(otherOwner.getEmail(),detail.path("host").path("email").asText());
        assertEquals(otherGuest.getEmail(),detail.path("guest").path("email").asText());
    }

    @Test void wildcardCharactersAreLiteralAndEmptySearchDoesNotRestrictResults()throws Exception{
        booking(guestUser,owner,"Regular Harbor","MYR",checkIn,true);
        var literal=booking(literalGuest,otherOwner,"100%_ Harbor","MYR",checkIn.plusDays(1),true);
        refund(literal,"FAILED");
        assertEquals(1,count("/api/admin/bookings?search="+encoded("%_")));
        assertEquals(1,count("/api/admin/bookings?hotel="+encoded("%_")));
        assertEquals(1,count("/api/admin/refunds?search="+encoded("%_")+"&hotel="+encoded("%_")));
        assertEquals(2,count("/api/admin/bookings?search="+encoded("   ")+"&hotel="));
    }

    @Test void bookingPaginationIsStableAndInvalidFiltersFailValidation()throws Exception{
        var first=booking(guestUser,owner,"First Hotel","MYR",checkIn,true);
        var second=booking(otherGuest,otherOwner,"Second Hotel","MYR",checkIn.plusDays(1),false);
        var third=booking(guestUser,owner,"Third Hotel","MYR",checkIn.plusDays(2),true);
        var page=ok("/api/admin/bookings?page=0&size=1");
        assertEquals(3,page.path("totalElements").asInt());
        assertEquals(3,page.path("totalPages").asInt());
        assertEquals(third.id(),page.path("items").get(0).path("booking").path("id").asLong());
        assertEquals(second.id(),ok("/api/admin/bookings?page=1&size=1").path("items").get(0).path("booking").path("id").asLong());
        assertEquals(first.id(),ok("/api/admin/bookings?page=2&size=1").path("items").get(0).path("booking").path("id").asLong());
        assertEquals(0,ok("/api/admin/bookings?page=3&size=1").path("items").size());
        for(String query:List.of("page=-1","size=0","size=101","status=UNKNOWN","paymentStatus=UNKNOWN","hotelId=0","hotelId=-1","hotelId=invalid",
            "checkInFrom=not-a-date","checkInTo=2032-02-30","checkInFrom=2032-05-01&checkInTo=2032-04-01","search="+"x".repeat(255),"hotel="+"x".repeat(201))){
            var response=admin.get("/api/admin/bookings?"+query);
            assertEquals(400,response.statusCode(),query+": "+response.body());
        }
    }

    @Test void detailPreservesExactOriginalCurrencyNightPricesAndOnlyCuratedPaymentFields()throws Exception{
        var myr=booking(guestUser,owner,"Harbor House","MYR",checkIn,true);
        var yen=booking(otherGuest,otherOwner,"Tokyo Garden","JPY",checkIn.plusDays(2),true);
        refund(yen,"SUCCEEDED");
        var myrResponse=admin.get(path(myr));
        assertEquals(200,myrResponse.statusCode(),myrResponse.body());
        var detail=tree(myrResponse);
        assertEquals("test",detail.path("paymentMode").asText());
        assertEquals(0,new BigDecimal("597.00").compareTo(detail.path("booking").path("total").decimalValue()));
        assertEquals(2,detail.path("nights").size());
        assertEquals(checkIn.toString(),detail.path("nights").get(0).path("date").asText());
        assertEquals(0,new BigDecimal("149.25").compareTo(detail.path("nights").get(1).path("unitPrice").decimalValue()));
        assertEquals(59700,detail.path("payment").path("amountMinor").asLong());
        assertEquals("MYR",detail.path("payment").path("currencyCode").asText());
        assertEquals("PAID",detail.path("payment").path("status").asText());
        assertEquals("cs_test_reporting_"+myr.id(),detail.path("payment").path("sessionId").asText());
        assertEquals("pi_reporting_"+myr.id(),detail.path("payment").path("paymentIntentId").asText());
        assertFalse(detail.path("payment").path("createdAt").isNull());
        assertFalse(detail.path("payment").path("paidAt").isNull());
        assertFalse(detail.path("payment").path("expiresAt").isNull());
        assertFalse(detail.path("payment").path("cancellationRequested").asBoolean());
        assertTrue(detail.path("refund").isNull());
        assertEquals(3,detail.path("guest").size());
        assertEquals(3,detail.path("host").size());
        var yenDetail=ok(path(yen));
        assertEquals("JPY",yenDetail.path("booking").path("currencyCode").asText());
        assertEquals(0,new BigDecimal("24000").compareTo(yenDetail.path("booking").path("total").decimalValue()));
        assertEquals(24000,yenDetail.path("payment").path("amountMinor").asLong());
        assertEquals(24000,yenDetail.path("refund").path("amountMinor").asLong());
        assertEquals("JPY",yenDetail.path("refund").path("currencyCode").asText());
        assertEquals("SUCCEEDED",yenDetail.path("refund").path("status").asText());
        assertEquals("FULL_BEFORE_CHECKIN_V1",yenDetail.path("refund").path("policyVersion").asText());
        assertFalse(yenDetail.path("refund").path("createdAt").isNull());
        assertFalse(yenDetail.path("refund").path("updatedAt").isNull());
        for(String route:List.of(path(myr),path(yen),"/api/admin/bookings","/api/admin/refunds"))assertPrivateFieldsAbsent(admin.get(route).body());
    }

    @Test void noAttemptOpenCheckoutAndExpiredReservationsRemainInspectableWithoutLeakingCheckoutUrl()throws Exception{
        var noAttempt=booking(guestUser,owner,"Pending Hotel","MYR",checkIn,false);
        var open=booking(guestUser,owner,"Open Checkout","MYR",checkIn.plusDays(1),false);
        openPayment(open);
        var expired=booking(guestUser,otherOwner,"Expired Hold","MYR",checkIn.plusDays(2),false);
        tx.executeWithoutResult(ignored->bookings.findById(expired.id()).orElseThrow().expire());
        var detail=ok(path(noAttempt));
        assertTrue(detail.path("payment").isNull());
        assertTrue(detail.path("refund").isNull());
        assertEquals("PENDING_PAYMENT",detail.path("booking").path("status").asText());
        var openResponse=admin.get(path(open));
        assertEquals(200,openResponse.statusCode(),openResponse.body());
        var openDetail=tree(openResponse);
        assertEquals("OPEN",openDetail.path("payment").path("status").asText());
        assertTrue(openDetail.path("payment").path("paidAt").isNull());
        assertTrue(openDetail.path("payment").path("paymentIntentId").isNull());
        assertPrivateFieldsAbsent(openResponse.body());
        assertEquals("EXPIRED",ok(path(expired)).path("booking").path("status").asText());
        assertEquals("NOT_REQUESTED",ok("/api/admin/bookings?hotelId="+noAttempt.hotelId()).path("items").get(0).path("refundStatus").asText());
        tx.executeWithoutResult(ignored->bookings.findById(noAttempt.id()).orElseThrow().freeConfirmed());
        assertEquals(1,count("/api/admin/bookings?paymentStatus=NOT_REQUIRED"));
    }

    @Test void refundQueueGroupsEveryStateAndCombinesBookingFilters()throws Exception{
        var initializing=booking(guestUser,owner,"Harbor House","MYR",checkIn,true);refund(initializing,"INITIALIZING");
        var pending=booking(guestUser,owner,"Harbor House","MYR",checkIn.plusDays(1),true);refund(pending,"PENDING");
        var succeeded=booking(otherGuest,otherOwner,"Tokyo Garden","JPY",checkIn.plusDays(2),true);refund(succeeded,"SUCCEEDED");
        var failed=booking(guestUser,owner,"Harbor House","MYR",checkIn.plusDays(3),true);refund(failed,"FAILED");
        var manual=booking(otherGuest,otherOwner,"Tokyo Garden","JPY",checkIn.plusDays(4),true);refund(manual,"REVIEW_REQUIRED");
        assertEquals(5,count("/api/admin/refunds"));
        assertEquals(2,count("/api/admin/refunds?status=NEEDS_REVIEW"));
        assertEquals(2,count("/api/admin/refunds?status=PROCESSING"));
        for(String state:List.of("INITIALIZING","PENDING","SUCCEEDED","FAILED","REVIEW_REQUIRED"))assertEquals(1,count("/api/admin/refunds?status="+state),state);
        var result=ok("/api/admin/refunds?status=NEEDS_REVIEW&hotel=Tokyo&hotelId="+manual.hotelId()+"&checkInFrom="+checkIn.plusDays(4)+"&checkInTo="+checkIn.plusDays(4)+"&search="+encoded(otherGuest.getEmail()));
        assertEquals(1,result.path("totalElements").asInt());
        var item=result.path("items").get(0);
        assertEquals(manual.id(),item.path("booking").path("id").asLong());
        assertEquals("REVIEW_REQUIRED",item.path("refund").path("status").asText());
        assertEquals(otherGuest.getEmail(),item.path("guest").path("email").asText());
        assertEquals(otherOwner.getEmail(),item.path("host").path("email").asText());
        assertEquals("FAILED",ok("/api/admin/bookings?search="+failed.reference()).path("items").get(0).path("refundStatus").asText());
        assertEquals(0,count("/api/admin/refunds?search=no-such-reference"));
    }

    @Test void refundPaginationUsesRefundCreationOrderAndRejectsInvalidFilters()throws Exception{
        var olderBooking=booking(guestUser,owner,"Older Booking","MYR",checkIn,true);
        var newerBooking=booking(guestUser,owner,"Newer Booking","MYR",checkIn.plusDays(1),true);
        long olderRefund=refund(newerBooking,"FAILED");
        long newerRefund=refund(olderBooking,"REVIEW_REQUIRED");
        var page=ok("/api/admin/refunds?status=NEEDS_REVIEW&page=0&size=1");
        assertEquals(2,page.path("totalElements").asInt());
        assertEquals(newerRefund,page.path("items").get(0).path("refund").path("id").asLong());
        assertEquals(olderBooking.id(),page.path("items").get(0).path("booking").path("id").asLong());
        assertEquals(olderRefund,ok("/api/admin/refunds?page=1&size=1").path("items").get(0).path("refund").path("id").asLong());
        for(String query:List.of("page=-1","size=0","size=101","status=PAID","hotelId=-1","checkInFrom=bad-date",
            "checkInFrom=2032-05-01&checkInTo=2032-04-01","search="+"x".repeat(255),"hotel="+"x".repeat(201))){
            var response=admin.get("/api/admin/refunds?"+query);
            assertEquals(400,response.statusCode(),query+": "+response.body());
        }
    }

    @Test void repeatedReportingGetsNeverChangeBookingsPaymentsRefundsInventoryOrEvents()throws Exception{
        var pending=booking(guestUser,owner,"Held Rooms","MYR",checkIn,false);
        openPayment(pending);
        var failed=booking(guestUser,owner,"Refund Failure","MYR",checkIn.plusDays(2),true);refund(failed,"FAILED");
        var review=booking(otherGuest,otherOwner,"Manual Review","JPY",checkIn.plusDays(4),true);refund(review,"REVIEW_REQUIRED");
        jdbc.update("update stay_bookings set expires_at=? where id=?",Instant.now().minusSeconds(60),pending.id());
        var before=snapshot();
        for(int i=0;i<3;i++){
            ok("/api/admin/bookings");
            ok("/api/admin/refunds?status=NEEDS_REVIEW");
            for(var fixture:List.of(pending,failed,review))ok(path(fixture));
        }
        assertEquals(before,snapshot());
        assertEquals(BookingStatus.PENDING_PAYMENT,bookings.findById(pending.id()).orElseThrow().getStatus());
        assertEquals("OPEN",payments.findByBookingId(pending.id()).orElseThrow().getStatus());
    }

    Map<String,List<Map<String,Object>>> snapshot(){
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("stay_bookings","stay_booking_nights","stay_payment_attempts","stay_refund_attempts","stay_room_inventory","stay_notifications","stay_payment_events")){
            result.put(table,jdbc.queryForList("select * from "+table+" order by id"));
        }
        return result;
    }

    void assertPrivateFieldsAbsent(String body){
        for(String forbidden:List.of("passwordHash","passwordVersion","requestKey","attemptKey","sessionUrl","checkout.stripe.com","private_fixture_",hash)){
            assertFalse(body.contains(forbidden),"Admin DTO leaked "+forbidden);
        }
    }
}
