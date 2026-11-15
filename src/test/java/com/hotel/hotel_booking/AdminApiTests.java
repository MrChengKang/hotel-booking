package com.hotel.hotel_booking;

import com.hotel.hotel_booking.booking.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.notification.NotificationRepository;
import com.hotel.hotel_booking.user.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:admin;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.payments.enabled=false", "app.admin.bootstrap.enabled=false"})
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminApiTests {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("stay-admin-images-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.media.directory",directory::toString);}
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired HotelRepository hotels; @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory; @Autowired HotelImageRepository images; @Autowired BookingRepository bookings;
    @Autowired NotificationRepository notifications; @Autowired InventoryService stock; @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    String hash; byte[] png; User hostUser;
    Session admin,host,otherHost,guest,anonymous;
    LocalDate from=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(30);
    @BeforeAll void fixture()throws Exception{
        hash=encoder.encode("SampleOnly123");var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB),"png",out);png=out.toByteArray();
    }
    @BeforeEach void setup()throws Exception{
        clean();
        users.save(new User("Platform Admin","admin-review@example.com",hash,Role.ADMIN));
        hostUser=users.save(new User("Hotel Owner","owner-review@example.com",hash,Role.HOST));
        users.save(new User("Another Owner","other-review@example.com",hash,Role.HOST));
        users.save(new User("Guest Traveller","traveller-review@example.com",hash,Role.USER));
        admin=login("admin-review@example.com");host=login(hostUser.getEmail());otherHost=login("other-review@example.com");
        guest=login("traveller-review@example.com");anonymous=new Session();
    }
    void clean()throws Exception{
        notifications.deleteAllInBatch();jdbc.update("delete from stay_favorite_hotels");jdbc.update("delete from stay_booking_nights");bookings.deleteAllInBatch();
        images.deleteAllInBatch();inventory.deleteAllInBatch();rooms.deleteAllInBatch();hotels.deleteAllInBatch();users.deleteAllInBatch();
        try(var files=Files.list(directory)){for(var file:files.toList())Files.delete(file);}
    }
    @AfterEach void cleanup()throws Exception{clean();}
    @AfterAll void removeDirectory()throws Exception{Files.delete(directory);}
    class Session {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body)throws Exception{return send(method,path,body,true);}
        HttpResponse<String> send(String method,String path,String body,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(25));
            if(csrf&&!method.equals("GET"))request.header("X-CSRF-TOKEN",tree(send("GET","/api/auth/csrf",null,false)).path("token").asText());
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> upload(long hotel)throws Exception{
            var body=new ByteArrayOutputStream();body.write("--admin-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"hotel.png\"\r\nContent-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));body.write(png);body.write("\r\n--admin-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
            var token=tree(send("GET","/api/auth/csrf",null)).path("token").asText();
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/host/hotels/"+hotel+"/images"))
                .header("X-CSRF-TOKEN",token).header("Content-Type","multipart/form-data; boundary=admin-boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            return client.send(request,HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{
        var session=new Session();var result=session.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}");
        assertEquals(200,result.statusCode(),result.body());return session;
    }
    JsonNode tree(HttpResponse<String> response){return json.readTree(response.body());}
    long hotel(){return hotels.save(new Hotel(hostUser,"Review Hotel","review-hotel","A complete hotel description","1 Test Road","Raub","MY","MYR","Asia/Kuala_Lumpur")).getId();}
    long room(long hotel){return rooms.save(new RoomType(hotels.findById(hotel).orElseThrow(),"KING","King room","Room description",2,1,3,new java.math.BigDecimal("149"))).getId();}
    long upload(long hotel)throws Exception{var response=host.upload(hotel);assertEquals(201,response.statusCode(),response.body());return tree(response).path("id").asLong();}
    long[] ready()throws Exception{long h=hotel(),r=room(h);stock.initializeNights(r,hostUser.getId(),from,from.plusDays(3));long image=upload(h);return new long[]{h,r,image};}
    String hostPath(long id){return "/api/host/hotels/"+id;}
    String adminPath(long id){return "/api/admin/hotels/"+id;}
    void submit(long h)throws Exception{var response=host.send("POST",hostPath(h)+"/submit",null);assertEquals(200,response.statusCode(),response.body());}
    long version(long h)throws Exception{var response=admin.send("GET",adminPath(h),null);assertEquals(200,response.statusCode(),response.body());return tree(response).path("hotel").path("version").asLong();}
    String decision(String kind,long version,String note){return "{\"decision\":\""+kind+"\",\"expectedVersion\":"+version+",\"note\":\""+note+"\"}";}
    void approve(long h)throws Exception{var response=admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",version(h),"Checked listing"));assertEquals(200,response.statusCode(),response.body());}

    @Test void adminRoutesAreRoleProtectedAndDirectoryNeverExposesPasswords()throws Exception{
        assertEquals(401,anonymous.send("GET","/api/admin/overview",null).statusCode());
        assertEquals(403,guest.send("GET","/api/admin/overview",null).statusCode());assertEquals(403,host.send("GET","/api/admin/users",null).statusCode());
        var overview=admin.send("GET","/api/admin/overview",null);assertEquals(200,overview.statusCode(),overview.body());
        var stats=tree(overview);assertEquals(2,stats.path("hosts").asLong());assertEquals(1,stats.path("admins").asLong());assertEquals("test",stats.path("paymentMode").asText());
        var result=admin.send("GET","/api/admin/users?search=traveller-review&page=0&size=20",null);assertEquals(200,result.statusCode(),result.body());
        assertEquals(1,tree(result).path("totalElements").asInt());assertEquals("USER",tree(result).path("items").get(0).path("role").asText());
        assertFalse(result.body().contains("passwordHash"));assertFalse(result.body().contains("passwordVersion"));assertFalse(result.body().contains(hash));
        assertEquals(400,admin.send("GET","/api/admin/users?size=101",null).statusCode());
        assertEquals(400,anonymous.send("POST","/api/auth/register","{\"name\":\"Fake Admin\",\"email\":\"forged@example.com\",\"password\":\"SampleOnly123\",\"role\":\"admin\"}").statusCode());
        assertFalse(users.existsByEmail("forged@example.com"));
    }
    @Test void submissionRequiresDescriptionActiveRoomHotelPhotoAndSellableInventory()throws Exception{
        long h=hotel();long photo=upload(h);assertEquals(409,host.send("POST",hostPath(h)+"/submit",null).statusCode());
        long r=room(h);assertEquals(409,host.send("POST",hostPath(h)+"/submit",null).statusCode());
        stock.initializeNights(r,hostUser.getId(),from,from.plusDays(3));host.send("DELETE",hostPath(h)+"/images/"+photo,null);
        assertEquals(409,host.send("POST",hostPath(h)+"/submit",null).statusCode());upload(h);
        assertEquals(200,host.send("PUT",hostPath(h),"{\"name\":\"Review Hotel\",\"description\":\"\",\"address\":\"1 Test Road\",\"city\":\"Raub\"}").statusCode());
        assertEquals(409,host.send("POST",hostPath(h)+"/submit",null).statusCode());
        host.send("PUT",hostPath(h),"{\"name\":\"Review Hotel\",\"description\":\"Ready for guests\",\"address\":\"1 Test Road\",\"city\":\"Raub\"}");
        submit(h);var view=tree(host.send("GET",hostPath(h),null));assertEquals("DRAFT",view.path("status").asText());assertEquals("PENDING",view.path("reviewStatus").asText());
        assertEquals(404,anonymous.send("GET","/api/hotels/"+h,null).statusCode());
    }
    @Test void ownerCannotPublishDirectlyAndAdminApprovalMakesListingAndPhotosPublic()throws Exception{
        var f=ready();long h=f[0],photo=f[2];
        assertEquals(409,host.send("PATCH",hostPath(h)+"/status","{\"status\":\"PUBLISHED\"}").statusCode());
        assertEquals(404,otherHost.send("POST",hostPath(h)+"/submit",null).statusCode());assertEquals(403,guest.send("POST",hostPath(h)+"/submit",null).statusCode());
        submit(h);assertEquals(200,admin.send("GET","/api/media/"+photo,null).statusCode());
        assertEquals(404,guest.send("GET","/api/media/"+photo,null).statusCode());assertEquals(404,otherHost.send("GET","/api/media/"+photo,null).statusCode());
        var pending=admin.send("GET","/api/admin/hotels?reviewStatus=PENDING",null);assertEquals(1,tree(pending).path("totalElements").asInt());
        var detail=tree(admin.send("GET",adminPath(h),null));assertEquals(1,detail.path("rooms").size());assertEquals(1,detail.path("images").size());assertEquals(hostUser.getEmail(),detail.path("host").path("email").asText());
        long version=version(h);assertEquals(403,host.send("POST",adminPath(h)+"/decision",decision("APPROVE",version,"Checked")).statusCode());
        assertEquals(403,admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",version,"Checked"),false).statusCode());
        approve(h);assertEquals("PUBLISHED",tree(anonymous.send("GET","/api/hotels/"+h,null)).path("status").asText());
        assertEquals(200,anonymous.send("GET","/api/media/"+photo,null).statusCode());assertEquals("APPROVED",tree(host.send("GET",hostPath(h),null)).path("reviewStatus").asText());
        assertEquals(409,admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",version,"Duplicate click")).statusCode());
    }
    @Test void rejectionRequiresReasonAndHostCanEditAndResubmit()throws Exception{
        long h=ready()[0];submit(h);long v=version(h);
        assertTrue(Set.of(400,409).contains(admin.send("POST",adminPath(h)+"/decision",decision("REJECT",v," ")).statusCode()));
        var rejected=admin.send("POST",adminPath(h)+"/decision",decision("REJECT",v,"Please improve the description"));assertEquals(200,rejected.statusCode(),rejected.body());
        var hostView=tree(host.send("GET",hostPath(h),null));assertEquals("REJECTED",hostView.path("reviewStatus").asText());assertEquals("Please improve the description",hostView.path("reviewNote").asText());
        assertEquals(404,anonymous.send("GET","/api/hotels/"+h,null).statusCode());
        assertEquals(200,host.send("PUT",hostPath(h),"{\"name\":\"Review Hotel\",\"description\":\"Improved and complete description\",\"address\":\"1 Test Road\",\"city\":\"Raub\"}").statusCode());
        submit(h);assertEquals("PENDING",tree(host.send("GET",hostPath(h),null)).path("reviewStatus").asText());
        assertEquals(409,admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",v,"Stale review")).statusCode());approve(h);
    }
    @Test void pendingReviewFreezesHostWritesButAllowsReadingAndWithdrawal()throws Exception{
        var f=ready();long h=f[0],r=f[1],photo=f[2];submit(h);
        assertEquals(409,host.send("PUT",hostPath(h),"{\"name\":\"Changed\",\"description\":\"Changed\",\"address\":\"1 Road\",\"city\":\"Raub\"}").statusCode());
        assertEquals(409,host.send("POST",hostPath(h)+"/room-types","{\"code\":\"NEW\",\"name\":\"New room\",\"maxGuests\":2,\"beds\":1,\"totalRooms\":1,\"basePrice\":100}").statusCode());
        assertEquals(409,host.send("PUT","/api/host/room-types/"+r,"{\"name\":\"Changed\",\"basePrice\":100,\"active\":true}").statusCode());
        assertEquals(409,host.send("POST","/api/host/room-types/"+r+"/inventory/initialize","{\"from\":\""+from+"\",\"to\":\""+from.plusDays(2)+"\"}").statusCode());
        assertEquals(409,host.send("PUT","/api/host/room-types/"+r+"/inventory/"+from,"{\"totalRooms\":3,\"blockedRooms\":0,\"nightlyPrice\":100,\"stopSell\":false}").statusCode());
        assertEquals(409,host.upload(h).statusCode());assertEquals(409,host.send("PATCH",hostPath(h)+"/images/"+photo,"{\"altText\":\"Changed\"}").statusCode());
        assertEquals(409,host.send("PUT",hostPath(h)+"/images/order","{\"imageIds\":["+photo+"]}").statusCode());assertEquals(409,host.send("DELETE",hostPath(h)+"/images/"+photo,null).statusCode());
        assertEquals(200,host.send("GET",hostPath(h)+"/room-types",null).statusCode());assertEquals(200,host.send("GET",hostPath(h)+"/images",null).statusCode());
        assertEquals(200,host.send("PATCH",hostPath(h)+"/status","{\"status\":\"DRAFT\"}").statusCode());
        assertEquals(409,admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",version(h),"Withdrawn")).statusCode());
        assertEquals(200,host.send("PUT","/api/host/room-types/"+r,"{\"name\":\"Changed\",\"basePrice\":100,\"active\":true}").statusCode());
    }
    @Test void changingPublishedMetadataRequiresReviewAgainWithoutChangingExistingOrders()throws Exception{
        var f=ready();long h=f[0],r=f[1];submit(h);approve(h);
        var created=guest.send("POST","/api/bookings","{\"roomTypeId\":"+r+",\"checkIn\":\""+from+"\",\"checkOut\":\""+from.plusDays(1)+"\",\"rooms\":1,\"guests\":2,\"requestKey\":\""+UUID.randomUUID()+"\"}");assertEquals(201,created.statusCode(),created.body());long booking=tree(created).path("booking").path("id").asLong();
        var before=guest.send("GET","/api/bookings/"+booking,null).body();
        var edited=host.send("PUT",hostPath(h),"{\"name\":\"New Hotel Name\",\"description\":\"Changed hotel description\",\"address\":\"2 New Road\",\"city\":\"Raub\"}");assertEquals(200,edited.statusCode(),edited.body());
        assertEquals("DRAFT",tree(edited).path("status").asText());assertEquals(404,anonymous.send("GET","/api/hotels/"+h,null).statusCode());
        assertEquals(before,guest.send("GET","/api/bookings/"+booking,null).body());
        assertEquals(1,inventory.findByRoomTypeIdAndStayDate(r,from).orElseThrow().getBookedRooms());
    }
    @Test void legacyPublishedHotelRemainsPublicAndOnlyPendingReviewAcceptsDecision()throws Exception{
        long h=hotel();var existing=hotels.findById(h).orElseThrow();existing.publish();hotels.saveAndFlush(existing);
        assertEquals(200,anonymous.send("GET","/api/hotels/"+h,null).statusCode());
        assertEquals(409,admin.send("POST",adminPath(h)+"/decision",decision("REJECT",version(h),"Not submitted")).statusCode());
        assertEquals(200,anonymous.send("GET","/api/hotels/"+h,null).statusCode());
        assertEquals(400,admin.send("GET","/api/admin/hotels?reviewStatus=INVALID",null).statusCode());
    }
    @Test void publicAndFavoriteViewsNeverExposePrivateReviewMetadata()throws Exception{
        long h=ready()[0];submit(h);
        String note="Private platform assessment for host only";
        var accepted=admin.send("POST",adminPath(h)+"/decision",decision("APPROVE",version(h),note));assertEquals(200,accepted.statusCode(),accepted.body());
        var owner=tree(host.send("GET",hostPath(h),null));assertEquals(note,owner.path("reviewNote").asText());assertFalse(owner.path("reviewedAt").isNull());
        var publicHotel=anonymous.send("GET","/api/hotels/"+h,null);assertEquals(200,publicHotel.statusCode());assertPublicReviewMetadata(tree(publicHotel));
        var publicList=anonymous.send("GET","/api/hotels",null);assertEquals(200,publicList.statusCode());assertPublicReviewMetadata(tree(publicList).path("items").get(0));
        var saved=guest.send("PUT","/api/account/favorites/"+h,null);assertEquals(200,saved.statusCode(),saved.body());assertPublicReviewMetadata(tree(saved).path("hotel"));
        var favorites=guest.send("GET","/api/account/favorites",null);assertEquals(200,favorites.statusCode());assertPublicReviewMetadata(tree(favorites).get(0).path("hotel"));
    }
    void assertPublicReviewMetadata(JsonNode hotel){
        for(String field:List.of("reviewStatus","reviewNote","submittedAt","reviewedAt"))assertTrue(hotel.path(field).isNull()||hotel.path(field).isMissingNode(),field+" must not be public");
        assertEquals(0,hotel.path("version").asLong());
    }

    @Test void publicDestinationsReflectPublishedCitiesAndUseAvailableCover()throws Exception{
        var first=ready();
        jdbc.update("update stay_hotels set status='PUBLISHED' where id=?",first[0]);
        var second=hotels.save(new Hotel(hostUser,"Second Hotel","second-city","Description","2 Test Road","RAUB","MY","MYR","Asia/Kuala_Lumpur"));
        jdbc.update("update stay_hotels set status='PUBLISHED' where id=?",second.getId());
        hotels.save(new Hotel(hostUser,"Draft Hotel","draft-city","Description","3 Test Road","Other City","MY","MYR","Asia/Kuala_Lumpur")); // draft must not be counted
        var response=anonymous.send("GET","/api/hotels/destinations",null);
        assertEquals(200,response.statusCode(),response.body());var destinations=tree(response);
        assertEquals(1,destinations.size());assertEquals(2,destinations.get(0).path("hotelCount").asInt());
        assertEquals("raub",destinations.get(0).path("city").asText().toLowerCase());
        assertEquals("/api/media/"+first[2],destinations.get(0).path("coverImageUrl").asText());
        assertFalse(response.body().contains(hostUser.getEmail()));
        for(int n=0;n<13;n++){
            var extra=hotels.save(new Hotel(hostUser,"Extra "+n,"extra-city-"+n,"Description","Street","City "+n,"MY","MYR","Asia/Kuala_Lumpur"));
            jdbc.update("update stay_hotels set status='PUBLISHED' where id=?",extra.getId());
        }
        var limited=tree(anonymous.send("GET","/api/hotels/destinations",null));
        assertEquals(12,limited.size());assertEquals(2,limited.get(0).path("hotelCount").asInt());
        jdbc.update("update stay_hotels set status='ARCHIVED'");
        assertEquals(0,tree(anonymous.send("GET","/api/hotels/destinations",null)).size());
    }
}
