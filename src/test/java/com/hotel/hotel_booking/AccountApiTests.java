package com.hotel.hotel_booking;

import com.hotel.hotel_booking.account.*;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:account;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountApiTests {
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired HotelRepository hotels;
    @Autowired AccountProfileRepository profiles; @Autowired FavoriteHotelRepository favorites;
    @Autowired PasswordEncoder encoder;
    Session guest,host,anonymous;long hotel,draft;String hash;
    void clean(){favorites.deleteAllInBatch();profiles.deleteAllInBatch();hotels.deleteAllInBatch();users.deleteAllInBatch();}
    @BeforeAll void password(){hash=encoder.encode("SampleOnly123");}
    @BeforeEach void setup()throws Exception{
        clean();var owner=users.save(new User("Host","accounthost@example.com",hash,Role.HOST));users.save(new User("Guest","accountguest@example.com",hash,Role.USER));
        var h=new Hotel(owner,"Favorite Hotel","favorite-hotel","Public description","1 Road","Raub","MY","MYR","Asia/Kuala_Lumpur");h.publish();hotel=hotels.save(h).getId();
        draft=hotels.save(new Hotel(owner,"Private draft hotel","private-draft","Secret draft description","Private address","Raub","MY","MYR","Asia/Kuala_Lumpur")).getId();
        guest=login("accountguest@example.com");host=login("accounthost@example.com");anonymous=new Session();
    }
    @AfterEach void teardown(){clean();}
    class Session {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body)throws Exception{return send(method,path,body,true);}
        HttpResponse<String> send(String method,String path,String body,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
            if(csrf&&!method.equals("GET"))request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null,false).body(),"token"));
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{var s=new Session();assertEquals(200,s.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());return s;}
    static String field(String json,String name){var m=Pattern.compile("\""+name+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),json);return m.group(1);}
    String favorite(){return "/api/account/favorites/"+hotel;}
    @Test void profileEditsPersistAndCannotChangeAccountIdentityOrRole()throws Exception{
        assertEquals(401,anonymous.send("GET","/api/account/profile",null).statusCode());
        var initial=guest.send("GET","/api/account/profile",null);assertEquals(200,initial.statusCode());assertTrue(initial.body().contains("\"phone\":\"\""));assertFalse(initial.body().contains("password"));
        String body="{\"name\":\"  Alex Guest  \",\"phone\":\"+60 12 345 6789\",\"city\":\" Raub \",\"bio\":\"Love quiet hotels\",\"email\":\"accounthost@example.com\",\"role\":\"admin\",\"userId\":999}";
        assertEquals(403,guest.send("PUT","/api/account/profile",body,false).statusCode());
        var updated=guest.send("PUT","/api/account/profile",body);assertEquals(200,updated.statusCode(),updated.body());assertEquals("Alex Guest",field(updated.body(),"name"));assertEquals("accountguest@example.com",field(updated.body(),"email"));assertEquals("user",field(updated.body(),"role"));
        var next=login("accountguest@example.com");assertEquals("Alex Guest",field(next.send("GET","/api/auth/me",null).body(),"name"));assertEquals("Raub",field(next.send("GET","/api/account/profile",null).body(),"city"));
        assertEquals("Host",field(host.send("GET","/api/account/profile",null).body(),"name"));
        assertEquals(400,guest.send("PUT","/api/account/profile","{\"name\":\"   \"}").statusCode());
        assertEquals(400,guest.send("PUT","/api/account/profile","{\"name\":\"Valid\",\"phone\":\"invalid-phone\"}").statusCode());
    }
    @Test void favoritesArePrivatePersistentAndIdempotent()throws Exception{
        assertEquals(401,anonymous.send("GET","/api/account/favorites",null).statusCode());assertEquals(403,guest.send("PUT",favorite(),null,false).statusCode());
        assertEquals(200,guest.send("PUT",favorite(),null).statusCode());assertEquals(200,guest.send("PUT",favorite(),null).statusCode());assertEquals(1,favorites.count());
        assertTrue(login("accountguest@example.com").send("GET","/api/account/favorites",null).body().contains("Favorite Hotel"));assertEquals("[]",host.send("GET","/api/account/favorites",null).body());
        assertEquals(204,host.send("DELETE",favorite(),null).statusCode());assertEquals(1,favorites.count());
        assertEquals(204,guest.send("DELETE",favorite(),null).statusCode());assertEquals(204,guest.send("DELETE",favorite(),null).statusCode());assertEquals(0,favorites.count());
    }
    @Test void unpublishedHotelsExposeNoDraftMetadataAndRemainRemovable()throws Exception{
        assertEquals(404,guest.send("PUT","/api/account/favorites/"+draft,null).statusCode());assertEquals(404,guest.send("PUT","/api/account/favorites/999999",null).statusCode());
        guest.send("PUT",favorite(),null);host.send("PATCH","/api/host/hotels/"+hotel+"/status","{\"status\":\"DRAFT\"}");
        var result=guest.send("GET","/api/account/favorites",null);assertEquals(200,result.statusCode());assertTrue(result.body().contains("\"hotel\":null"));assertFalse(result.body().contains("Favorite Hotel"));assertFalse(result.body().contains("1 Road"));
        assertEquals(204,guest.send("DELETE",favorite(),null).statusCode());assertEquals(0,favorites.count());
    }
    @Test void concurrentRetriesCreateOnlyOneFavorite()throws Exception{
        var second=login("accountguest@example.com");
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->guest.send("PUT",favorite(),null));var b=executor.submit(()->second.send("PUT",favorite(),null));
            assertEquals(200,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(200,b.get(25,TimeUnit.SECONDS).statusCode());
        }
        assertEquals(1,favorites.count());
    }
}
