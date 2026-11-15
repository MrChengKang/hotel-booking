package com.hotel.hotel_booking;

import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;
import com.hotel.hotel_booking.media.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:cloudimages;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Import(CloudinaryHotelImageApiTests.CloudFixture.class)
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudinaryHotelImageApiTests {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("stay-image-test-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.media.directory",directory::toString);
        registry.add("app.media.provider",()->"cloudinary");
        registry.add("app.media.cloudinary.cloud-name",()->"test-cloud");
        registry.add("app.media.cloudinary.api-key",()->"test-key");
        registry.add("app.media.cloudinary.api-secret",()->"test-secret");}
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms; @Autowired HotelImageRepository images; @Autowired PasswordEncoder encoder;
    Session owner,other,guest,anonymous;long hotel,room;byte[] png;
    @BeforeAll void setup() throws Exception {
        String hash=encoder.encode("SampleOnly123");
        users.save(new User("Owner","photoowner@example.com",hash,Role.HOST));users.save(new User("Other","photoother@example.com",hash,Role.HOST));users.save(new User("Guest","photoguest@example.com",hash,Role.USER));
        owner=login("photoowner@example.com");other=login("photoother@example.com");guest=login("photoguest@example.com");anonymous=new Session();
        hotel=id(owner.send("POST","/api/host/hotels","{\"name\":\"Photo Hotel\",\"slug\":\"photo-hotel\",\"address\":\"1 Road\",\"city\":\"Raub\",\"countryCode\":\"MY\",\"currencyCode\":\"MYR\",\"timeZone\":\"Asia/Kuala_Lumpur\"}").body());
        room=id(owner.send("POST","/api/host/hotels/"+hotel+"/room-types","{\"code\":\"KING\",\"name\":\"King\",\"maxGuests\":2,\"beds\":1,\"totalRooms\":1,\"basePrice\":100}").body());
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB),"png",out);png=out.toByteArray();
    }
    @BeforeEach void clear() throws Exception {remote.clear();cloudDown=false;images.deleteAllInBatch();try(var paths=Files.list(directory)){for(var path:paths.toList())Files.delete(path);}owner.send("PATCH","/api/host/hotels/"+hotel+"/status","{\"status\":\"DRAFT\"}");}
    @AfterAll void clean() throws Exception {images.deleteAllInBatch();rooms.deleteAllInBatch();hotels.deleteAllInBatch();users.deleteAllInBatch();try(var paths=Files.list(directory)){for(var path:paths.toList())Files.delete(path);}Files.delete(directory);}
    void publishFixture(){var h=hotels.findById(hotel).orElseThrow();h.publish();hotels.saveAndFlush(h);}
    String path(){return "/api/host/hotels/"+hotel+"/images";}
    class Session {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
            if(!method.equals("GET")){var csrf=send("GET","/api/auth/csrf",null);request.header("X-CSRF-TOKEN",field(csrf.body(),"token"));}
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> upload(String path,byte[] bytes,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","multipart/form-data; boundary=stay-boundary");
            if(csrf)request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null).body(),"token"));
            var body=new ByteArrayOutputStream();body.write("--stay-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../spoof.svg\"\r\nContent-Type: image/svg+xml\r\n\r\n".getBytes());body.write(bytes);body.write("\r\n--stay-boundary--\r\n".getBytes());
            return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{var s=new Session();assertEquals(200,s.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());return s;}
    static String field(String json,String field){var match=Pattern.compile("\""+field+"\":\"([^\"]*)\"").matcher(json);assertTrue(match.find(),json);return match.group(1);}
    static long id(String json){var match=Pattern.compile("\"id\":(\\d+)").matcher(json);assertTrue(match.find(),json);return Long.parseLong(match.group(1));}
    @Test void realMultipartValidatesBytesAndProtectsDraftMedia()throws Exception{
        var response=owner.upload(path(),png,true);assertEquals(201,response.statusCode(),response.body());long image=id(response.body());
        assertFalse(response.body().contains("storageKey"));assertFalse(response.body().contains(directory.toString()));
        assertEquals(404,anonymous.send("GET","/api/media/"+image,null).statusCode());assertEquals(404,other.send("GET","/api/media/"+image,null).statusCode());assertEquals(404,guest.send("GET","/api/media/"+image,null).statusCode());
        var media=owner.send("GET","/api/media/"+image,null);assertEquals(200,media.statusCode());assertEquals("image/png",media.headers().firstValue("content-type").orElse(""));assertTrue(media.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(404,other.upload(path(),png,true).statusCode());assertEquals(403,guest.upload(path(),png,true).statusCode());assertEquals(403,owner.upload(path(),png,false).statusCode());
        assertEquals(400,owner.upload(path(),"<svg><script>bad</script></svg>".getBytes(),true).statusCode());
        assertEquals(413,owner.upload(path(),new byte[5*1024*1024+1],true).statusCode());
        assertEquals(1,images.count());assertEquals(1,remote.size());
    }
    @Test void orderCoverCaptionsAndDeleteUpdatePublicViews()throws Exception{
        long first=id(owner.upload(path(),png,true).body()),second=id(owner.upload(path(),png,true).body());
        assertEquals(200,owner.send("PUT",path()+"/order","{\"imageIds\":["+second+","+first+"]}").statusCode());
        assertEquals("/api/media/"+second,field(owner.send("GET","/api/host/hotels/"+hotel,null).body(),"coverImageUrl"));
        assertEquals(409,owner.send("PUT",path()+"/order","{\"imageIds\":["+first+","+first+"]}").statusCode());
        assertEquals(200,owner.send("PATCH",path()+"/"+second,"{\"altText\":\"Sunny lobby\"}").statusCode());
        assertEquals(404,anonymous.send("GET","/api/hotels/"+hotel+"/images",null).statusCode());
        publishFixture(); // This fixture concerns media visibility; AdminApiTests verifies approval.
        assertEquals(200,anonymous.send("GET","/api/media/"+second,null).statusCode());assertTrue(anonymous.send("GET","/api/hotels/"+hotel+"/images",null).body().contains("Sunny lobby"));
        assertEquals(204,owner.send("DELETE",path()+"/"+second,null).statusCode());assertEquals(404,anonymous.send("GET","/api/media/"+second,null).statusCode());
        assertEquals("/api/media/"+first,field(anonymous.send("GET","/api/hotels/"+hotel,null).body(),"coverImageUrl"));
        assertEquals(1,remote.size());
        owner.send("PATCH","/api/host/hotels/"+hotel+"/status","{\"status\":\"DRAFT\"}");assertEquals(404,anonymous.send("GET","/api/media/"+first,null).statusCode());
    }
    @Test void roomCollectionsAreSeparateAndInactiveRoomsPrivate()throws Exception{
        long roomImage=id(owner.upload(path()+"?roomTypeId="+room,png,true).body());
        assertEquals("[]",owner.send("GET",path(),null).body());assertTrue(owner.send("GET",path()+"?roomTypeId="+room,null).body().contains("/api/media/"+roomImage));
        assertEquals(409,owner.send("PUT",path()+"/order","{\"imageIds\":["+roomImage+"]}").statusCode());
        assertEquals(404,owner.upload(path()+"?roomTypeId=999999",png,true).statusCode());
        publishFixture();assertEquals(200,anonymous.send("GET","/api/media/"+roomImage,null).statusCode());
        assertTrue(anonymous.send("GET","/api/hotels/"+hotel+"/room-types",null).body().contains("/api/media/"+roomImage));
        owner.send("PUT","/api/host/room-types/"+room,"{\"name\":\"King\",\"basePrice\":100,\"active\":false}");assertEquals(404,anonymous.send("GET","/api/media/"+roomImage,null).statusCode());assertEquals(200,owner.send("GET","/api/media/"+roomImage,null).statusCode());
        owner.send("PUT","/api/host/room-types/"+room,"{\"name\":\"King\",\"basePrice\":100,\"active\":true}");
    }

    static final java.util.Map<String,byte[]> remote=new ConcurrentHashMap<>();
    static boolean cloudDown;
    @TestConfiguration static class CloudFixture {
        @Bean @Primary CloudinaryGateway fakeCloud(CloudinarySettings settings,ObjectMapper json) {
            return new CloudinaryGateway(settings,json) {
                @Override public void upload(String key,byte[] bytes) {
                    if(cloudDown)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    remote.put(key,bytes.clone());
                }
                @Override public byte[] read(String key) {
                    if(!remote.containsKey(key))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
                    return remote.get(key).clone();
                }
                @Override public void delete(String key){remote.remove(key);}
            };
        }
    }
}
