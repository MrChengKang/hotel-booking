package com.hotel.hotel_booking;

import com.hotel.hotel_booking.account.*;
import com.hotel.hotel_booking.user.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:cloudavatars;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Import(CloudinaryAvatarApiTests.CloudFixture.class)
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudinaryAvatarApiTests {
    static final Path directory;
    static {try{directory=Files.createTempDirectory("stay-avatar-test-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.media.directory",directory::toString);
        registry.add("app.media.provider",()->"cloudinary");
        registry.add("app.media.cloudinary.cloud-name",()->"test-cloud");
        registry.add("app.media.cloudinary.api-key",()->"test-key");
        registry.add("app.media.cloudinary.api-secret",()->"test-secret");}
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired AccountAvatarRepository avatars; @Autowired AccountProfileRepository profiles;
    @Autowired PasswordEncoder encoder;
    Session owner,other,anonymous;byte[] png,jpeg;
    @BeforeAll void setup()throws Exception {
        String hash=encoder.encode("SampleOnly123");users.save(new User("Owner","avatarowner@example.com",hash,Role.USER));users.save(new User("Other","avatarother@example.com",hash,Role.HOST));
        owner=login("avatarowner@example.com");other=login("avatarother@example.com");anonymous=new Session();
        var image=new BufferedImage(40,30,BufferedImage.TYPE_INT_RGB);var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);png=output.toByteArray();output.reset();ImageIO.write(image,"jpg",output);jpeg=output.toByteArray();
    }
    @BeforeEach void clear()throws Exception{remote.clear();cloudDown=false;avatars.deleteAllInBatch();try(var files=Files.list(directory)){for(var path:files.toList())Files.delete(path);}}
    @AfterAll void clean()throws Exception{avatars.deleteAllInBatch();profiles.deleteAllInBatch();users.deleteAllInBatch();try(var files=Files.list(directory)){for(var path:files.toList())Files.delete(path);}Files.delete(directory);}
    class Session {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> send(String method,String path,String body)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(20));
            if(!method.equals("GET"))request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null).body(),"token"));
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> upload(byte[] bytes,boolean csrf)throws Exception{
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/account/avatar")).header("Content-Type","multipart/form-data; boundary=avatar-boundary");
            if(csrf)request.header("X-CSRF-TOKEN",field(send("GET","/api/auth/csrf",null).body(),"token"));
            var body=new ByteArrayOutputStream();body.write("--avatar-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../spoof.svg\"\r\nContent-Type: image/svg+xml\r\n\r\n".getBytes());body.write(bytes);body.write("\r\n--avatar-boundary--\r\n".getBytes());
            return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Session login(String email)throws Exception{var s=new Session();assertEquals(200,s.send("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"SampleOnly123\"}").statusCode());return s;}
    static String field(String json,String name){var m=Pattern.compile("\""+name+"\":\"([^\"]*)\"").matcher(json);assertTrue(m.find(),json);return m.group(1);}
    @Test void uploadPersistsInProfileAndNewLoginAndIsPrivate()throws Exception {
        var uploaded=owner.upload(png,true);assertEquals(200,uploaded.statusCode(),uploaded.body());String url=field(uploaded.body(),"avatarUrl");
        assertFalse(uploaded.body().contains("storageKey"));assertFalse(uploaded.body().contains(directory.toString()));
        var image=owner.send("GET",url,null);assertEquals(200,image.statusCode());assertEquals("image/png",image.headers().firstValue("content-type").orElse(""));assertTrue(image.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(401,anonymous.send("GET",url,null).statusCode());assertEquals(404,other.send("GET",url,null).statusCode());
        assertEquals(url,field(owner.send("GET","/api/account/profile",null).body(),"avatarUrl"));assertEquals(url,field(owner.send("GET","/api/auth/me",null).body(),"avatarUrl"));
        assertEquals(url,field(login("avatarowner@example.com").send("GET","/api/auth/me",null).body(),"avatarUrl"));
    }
    @Test void replacementAndRemovalCleanFilesAndInvalidatePreviousUrls()throws Exception {
        String first=field(owner.upload(png,true).body(),"avatarUrl"),second=field(owner.upload(jpeg,true).body(),"avatarUrl");assertNotEquals(first,second);assertEquals(1,avatars.count());
        assertEquals(404,owner.send("GET",first,null).statusCode());assertEquals("image/jpeg",owner.send("GET",second,null).headers().firstValue("content-type").orElse(""));
        assertEquals(1,remote.size());
        assertEquals(204,owner.send("DELETE","/api/account/avatar",null).statusCode());assertEquals(204,owner.send("DELETE","/api/account/avatar",null).statusCode());assertEquals(404,owner.send("GET",second,null).statusCode());
        assertTrue(owner.send("GET","/api/auth/me",null).body().contains("\"avatarUrl\":null"));assertEquals(0,remote.size());
    }
    @Test void invalidUploadsAndMissingCsrfPreserveExistingAvatar()throws Exception {
        String url=field(owner.upload(png,true).body(),"avatarUrl");
        assertEquals(403,owner.upload(jpeg,false).statusCode());assertEquals(400,owner.upload("<svg><script>bad</script></svg>".getBytes(),true).statusCode());assertEquals(400,owner.upload(new byte[0],true).statusCode());assertEquals(413,owner.upload(new byte[5*1024*1024+1],true).statusCode());
        assertEquals(url,field(owner.send("GET","/api/auth/me",null).body(),"avatarUrl"));assertEquals(200,owner.send("GET",url,null).statusCode());assertEquals(1,remote.size());
    }
    @Test void profileEditsAndOtherAccountsCannotReplaceOrRemoveOwnerAvatar()throws Exception {
        String url=field(owner.upload(png,true).body(),"avatarUrl");other.upload(jpeg,true);assertEquals(2,avatars.count());other.send("DELETE","/api/account/avatar",null);
        assertEquals(200,owner.send("GET",url,null).statusCode());
        var edit=owner.send("PUT","/api/account/profile","{\"name\":\"Renamed owner\",\"avatarUrl\":\"https://untrusted.invalid/avatar.svg\"}");assertEquals(200,edit.statusCode());assertEquals(url,field(edit.body(),"avatarUrl"));
        assertEquals(url,field(owner.send("GET","/api/auth/me",null).body(),"avatarUrl"));assertEquals(1,avatars.count());
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

    @Test void cloudOutagePreservesTheExistingAvatarAndNeverFallsBackToEphemeralFiles()throws Exception {
        String url=field(owner.upload(png,true).body(),"avatarUrl");
        cloudDown=true;
        assertEquals(503,owner.upload(jpeg,true).statusCode());
        assertEquals(url,field(owner.send("GET","/api/account/profile",null).body(),"avatarUrl"));
        assertEquals(200,owner.send("GET",url,null).statusCode());assertEquals(1,remote.size());
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
}
