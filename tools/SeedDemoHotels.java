package com.hotel.hotel_booking.tools;

import com.hotel.hotel_booking.HotelBookingApplication;
import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Explicit offline portfolio import. This class is never started by the web application. */
public class SeedDemoHotels {
    static final String EMAIL="demo.host@stay.example";
    static final String HOST_NAME="STAY Demo Host";
    static final String MARKER="Portfolio demo hotel.";
    static final int DAYS=90;
    static final String[] PHOTOS={"photo-1566073771259-6a8506099945","photo-1571896349842-33c89424de2d",
        "photo-1542314831-068cd1dbfeeb","photo-1564501049412-61c2a3083791",
        "photo-1566665797739-1674de7a421a","photo-1611892440504-42a792e24d32",
        "photo-1616594039964-ae9021a400a0","photo-1540518614846-7eded433c457"};
    record City(String name,String key,String country,String currency,String zone,int rate,String first,String second){}
    static final List<City> CITIES=List.of(
        new City("Kuala Lumpur","kuala-lumpur","MY","MYR","Asia/Kuala_Lumpur",180,"Meridian City Hotel","The Orchard Kuala Lumpur"),
        new City("George Town","george-town","MY","MYR","Asia/Kuala_Lumpur",155,"Harbour Lane Boutique","Amber Courtyard Hotel"),
        new City("Langkawi","langkawi","MY","MYR","Asia/Kuala_Lumpur",230,"Palm Cove Retreat","Azure Bay Hotel"),
        new City("Singapore","singapore","SG","SGD","Asia/Singapore",165,"Marina Grove Hotel","Cedar City Suites"),
        new City("Tokyo","tokyo","JP","JPY","Asia/Tokyo",12000,"Sakura Garden Hotel","Hikari Urban Stay"),
        new City("Paris","paris","FR","EUR","Europe/Paris",145,"Maison Lumiere","Rue des Jardins Hotel"),
        new City("London","london","GB","GBP","Europe/London",135,"The Willow House","Mayfair Garden Stay"),
        new City("New York","new-york","US","USD","America/New_York",175,"Hudson Corner Hotel","Parkside Loft Hotel")
    );
    record Result(boolean hostCreated,int hotels,int rooms,int nights,int photos,int skipped){}
    record Photo(byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}
        public String getOriginalFilename(){return "demo-stock.jpg";}
        public String getContentType(){return "image/jpeg";}
        public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;}
        public byte[] getBytes(){return bytes;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        public void transferTo(File file)throws IOException{Files.write(file.toPath(),bytes);}
    }
    static String slug(City city,int variant){return "demo-stay-v1-"+city.key()+"-"+(variant+1);}

    static Result seed(ConfigurableApplicationContext context,Path assets,String password){
        var users=context.getBean(UserRepository.class);
        var hotels=context.getBean(HotelRepository.class);
        var rooms=context.getBean(RoomTypeRepository.class);
        var images=context.getBean(HotelImageService.class);
        var management=context.getBean(HotelManagementService.class);
        var jdbc=context.getBean(JdbcTemplate.class);
        var bank=Arrays.stream(PHOTOS).map(id->{
            try{return new Photo(Files.readAllBytes(assets.resolve(id+".jpg")));}
            catch(IOException failure){throw new IllegalArgumentException("Download the photo bank first.");}
        }).toList();
        // Validate the whole photo bank before creating even the demo account.
        for(var photo:bank){
            if(photo.getSize()>5*1024*1024)throw new IllegalArgumentException("Demo photo exceeds upload limit.");
            try{if(javax.imageio.ImageIO.read(photo.getInputStream())==null)throw new IllegalArgumentException("Invalid demo JPG.");}
            catch(IOException failure){throw new IllegalArgumentException("Invalid demo JPG.");}
        }
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        return tx.execute(status->{
            var host=users.findByEmail(EMAIL).orElse(null);
            boolean hostCreated=host==null;
            if(host!=null&&(host.getRole()!=Role.HOST||!host.getName().equals(HOST_NAME)))
                throw new IllegalStateException("Demo email collision; existing account will not be changed.");
            // Preflight every slug before any persistent change.
            for(var city:CITIES)for(int variant=0;variant<2;variant++){
                var existing=hotels.findBySlug(slug(city,variant)).orElse(null);
                if(existing!=null&&(host==null||!existing.getHost().getId().equals(host.getId())))
                    throw new IllegalStateException("Demo slug collision; existing hotel will not be changed.");
            }
            if(hostCreated)host=users.saveAndFlush(new User(HOST_NAME,EMAIL,
                context.getBean(PasswordEncoder.class).encode(password),Role.HOST));
            int added=0,roomCount=0,nightCount=0,photoCount=0,skipped=0;
            for(int cityIndex=0;cityIndex<CITIES.size();cityIndex++){
                var city=CITIES.get(cityIndex);
                for(int variant=0;variant<2;variant++){
                    if(hotels.findBySlug(slug(city,variant)).isPresent()){skipped++;continue;}
                    String description=MARKER+" This fictional property in "+city.name()+" demonstrates STAY's hotel booking workflow. "
                        +"The address is illustrative and Unsplash stock photos do not depict a real business with this name. "
                        +"Enjoy a bright guest room, a comfortable bed and a relaxed base for exploring the city. "
                        +"Reservations and payments on this project are for sandbox demonstrations only.";
                    var hotel=hotels.saveAndFlush(new Hotel(host,"[Demo] "+(variant==0?city.first():city.second()),slug(city,variant),description,
                        "Demo district "+(variant+1)+" (fictional address)",city.name(),city.country(),city.currency(),city.zone()));
                    added++;
                    int cover=(cityIndex+variant*3)%PHOTOS.length;
                    images.upload(hotel.getId(),null,EMAIL,bank.get(cover),"Unsplash stock illustration - demo hotel");
                    images.upload(hotel.getId(),null,EMAIL,bank.get(4+(cityIndex+variant)%4),"Unsplash stock illustration - guest room");
                    images.upload(hotel.getId(),null,EMAIL,bank.get(4+(cityIndex+variant+1)%4),"Unsplash stock illustration - room interior");
                    photoCount+=3;
                    for(int type=0;type<2;type++){
                        BigDecimal rate=BigDecimal.valueOf(city.rate()).multiply(new BigDecimal(variant==0?"1.00":"1.10"))
                            .multiply(new BigDecimal(type==0?"1.00":"1.40")).setScale(2);
                        var room=rooms.saveAndFlush(new RoomType(hotel,type==0?"STANDARD_KING":"DELUXE_TWIN",
                            type==0?"Standard King":"Deluxe Twin","Demo room type with stock illustrative photography.",type==0?2:3,type==0?1:2,type==0?5:3,rate));
                        roomCount++;
                        images.upload(hotel.getId(),room.getId(),EMAIL,bank.get(4+(cityIndex+variant+type)%4),"Unsplash stock illustration - demo room");
                        photoCount++;
                        LocalDate start=LocalDate.now(ZoneId.of(city.zone()));
                        var dates=new ArrayList<LocalDate>();for(int day=0;day<DAYS;day++)dates.add(start.plusDays(day));
                        // All rows belong to this newly-created room. Prepared batch inserts avoid
                        // thousands of network round trips; no existing stock or bookings are updated.
                        jdbc.batchUpdate("insert into stay_room_inventory (room_type_id,stay_date,total_rooms,booked_rooms,blocked_rooms,nightly_price,stop_sell,updated_at,version) values (?,?,?,?,?,?,?,?,?)",
                            dates,50,(statement,date)->{
                                statement.setLong(1,room.getId());statement.setDate(2,java.sql.Date.valueOf(date));
                                statement.setInt(3,room.getTotalRooms());statement.setInt(4,0);statement.setInt(5,0);
                                statement.setBigDecimal(6,rate);statement.setBoolean(7,false);
                                statement.setTimestamp(8,Timestamp.from(Instant.now()));statement.setLong(9,0);
                            });
                        nightCount+=DAYS;
                    }
                    management.requireReviewReady(hotel);
                    // Trusted offline demo import only; the public Host API still requires review.
                    hotel.publish();hotels.flush();
                    System.out.println("DEMO_PROGRESS prepared="+added+"/16 city="+city.name());
                }
            }
            return new Result(hostCreated,added,roomCount,nightCount,photoCount,skipped);
        });
    }

    static void verify(ConfigurableApplicationContext context,Path assets,String password){
        var users=context.getBean(UserRepository.class);var hotels=context.getBean(HotelRepository.class);
        var rooms=context.getBean(RoomTypeRepository.class);var jdbc=context.getBean(JdbcTemplate.class);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        long[] manual=tx.execute(status->{
            var owner=users.saveAndFlush(new User("Existing owner","existing.owner@example.invalid","original-password-hash",Role.HOST));
            var hotel=hotels.saveAndFlush(new Hotel(owner,"Existing Hotel","manual-hotel-before-seed","Original description","Original address","Kuala Lumpur","MY","MYR","Asia/Kuala_Lumpur"));
            hotel.publish();hotels.flush();
            var room=rooms.saveAndFlush(new RoomType(hotel,"EXISTING","Existing room","Original room",2,1,3,new BigDecimal("88.00")));
            var date=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(2);
            var stock=new RoomInventory(room,date,3,new BigDecimal("88.00"));stock.reserve(1);context.getBean(RoomInventoryRepository.class).saveAndFlush(stock);
            var guest=users.saveAndFlush(new User("Existing guest","existing.guest@example.invalid","guest-password-hash",Role.USER));
            var booking=new com.hotel.hotel_booking.booking.Booking(guest,room,UUID.randomUUID().toString(),date,date.plusDays(1),1,2);
            booking.addNight(date,new BigDecimal("88.00"));context.getBean(com.hotel.hotel_booking.booking.BookingRepository.class).saveAndFlush(booking);
            return new long[]{owner.getId(),hotel.getId(),room.getId(),booking.getId()};
        });
        var first=seed(context,assets,password);
        if(first.hotels()!=16||first.rooms()!=32||first.nights()!=2880||first.photos()!=80)throw new IllegalStateException("Seed counts mismatch.");
        if(!context.getBean(PasswordEncoder.class).matches(password,users.findByEmail(EMAIL).orElseThrow().getPasswordHash()))
            throw new IllegalStateException("Demo host password was not hashed correctly.");
        var roomId=jdbc.queryForObject("select min(r.id) from stay_room_types r join stay_hotels h on h.id=r.hotel_id where h.slug like 'demo-stay-v1-%'",Long.class);
        jdbc.update("update stay_room_inventory set booked_rooms=1,nightly_price=199 where room_type_id=?",roomId);
        var second=seed(context,assets,"DO_NOT_CHANGE_EXISTING_PASSWORD");
        if(second.hostCreated()||second.hotels()!=0||second.rooms()!=0||second.nights()!=0||second.photos()!=0||second.skipped()!=16)
            throw new IllegalStateException("Import is not idempotent.");
        if(jdbc.queryForObject("select count(*) from stay_room_inventory where room_type_id=? and booked_rooms=1 and nightly_price=199",Integer.class,roomId)!=90)
            throw new IllegalStateException("Existing inventory changed.");
        if(!context.getBean(PasswordEncoder.class).matches(password,users.findByEmail(EMAIL).orElseThrow().getPasswordHash()))
            throw new IllegalStateException("Existing password changed.");
        var destinations=context.getBean(HotelManagementService.class).destinations();
        if(destinations.size()!=8||destinations.stream().anyMatch(d->d.hotelCount()!=(d.city().equals("Kuala Lumpur")?3:2)||d.coverImageUrl()==null))
            throw new IllegalStateException("Public destination counts/photos mismatch.");
        var firstHotel=hotels.findBySlug(slug(CITIES.get(0),0)).orElseThrow();
        var quotes=context.getBean(HotelManagementService.class).availability(firstHotel.getId(),LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(1),
            LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(3),1,2);
        if(quotes.size()!=2)throw new IllegalStateException("Demo rooms cannot be quoted.");
        if(jdbc.queryForObject("select count(*) from stay_bookings",Integer.class)!=1)throw new IllegalStateException("Seeder created or removed a booking.");
        if(!users.findById(manual[0]).orElseThrow().getPasswordHash().equals("original-password-hash")
            ||!hotels.findById(manual[1]).orElseThrow().getName().equals("Existing Hotel")
            ||jdbc.queryForObject("select count(*) from stay_room_inventory where room_type_id=? and booked_rooms=1 and nightly_price=88",Integer.class,manual[2])!=1)
            throw new IllegalStateException("Unrelated pre-existing records changed.");
        System.out.println("DEMO_VERIFIED H2: 16 hotels,32 rooms,2880 nights,80 photos; repeat preserved stock,passwords; 8 public cities and real availability quotes.");
    }

    public static void main(String[] args)throws Exception{
        var options=Set.of(args);boolean apply=options.contains("--apply-demo"),test=options.contains("--verify-h2");
        Path assets=Path.of(".demo-assets").toAbsolutePath();
        for(String arg:args)if(arg.startsWith("--assets="))assets=Path.of(arg.substring(9)).toAbsolutePath();
        if(!apply&&!test){System.out.println("DEMO_PLAN 1 Demo Host,16 hotels,8 cities,32 room types,90 days/room,2880 inventory rows,80 photo records. Existing demo slugs are skipped.");return;}
        var random=new byte[24];new SecureRandom().nextBytes(random);
        String password=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Path credentials=Path.of(".env.demo-host.local");
        var settings=new ArrayList<String>(List.of("--server.address=127.0.0.1","--server.port=0",
            "--server.servlet.session.cookie.name=STAY_SEED_ONLY","--spring.jpa.hibernate.ddl-auto="+(test?"create-drop":"validate"),
            "--spring.jpa.show-sql=false","--spring.main.banner-mode=off","--logging.level.root=ERROR",
            "--app.admin.bootstrap.enabled=false","--app.payments.enabled=false","--app.payments.scheduling-enabled=false",
            "--app.payments.stripe-secret=","--app.payments.webhook-secret=",
            "--spring.datasource.hikari.data-source-properties.rewriteBatchedStatements=true"));
        Path testMedia=test?Files.createTempDirectory("stay-demo-seed-test-"):null;
        if(test){
            settings.addAll(List.of("--spring.profiles.active=test","--spring.config.import=",
                "--spring.datasource.url=jdbc:h2:mem:demoseed;MODE=MySQL;DB_CLOSE_DELAY=-1","--spring.datasource.username=sa",
                "--spring.datasource.password=","--spring.datasource.driver-class-name=org.h2.Driver",
                "--app.media.directory="+testMedia));
        }
        try(var context=new SpringApplicationBuilder(HotelBookingApplication.class).run(settings.toArray(String[]::new))){
            if(test){verify(context,assets,password);return;}
            if(Files.exists(credentials)&&context.getBean(UserRepository.class).findByEmail(EMAIL).isEmpty())
                throw new IllegalStateException("A credentials file exists without the account; inspect it before importing.");
            var result=seed(context,assets,password);
            if(result.hostCreated())Files.writeString(credentials,"# Local demo host login. Do not commit or share.\nDEMO_HOST_EMAIL="+EMAIL+"\nDEMO_HOST_PASSWORD="+password+"\n",StandardOpenOption.CREATE_NEW);
            System.out.println("DEMO_IMPORTED hotels="+result.hotels()+" rooms="+result.rooms()+" inventory="+result.nights()+" photos="+result.photos()+" existingSkipped="+result.skipped()+" hostCreated="+result.hostCreated());
            System.out.println("DEMO_DONE Demo Host credentials are stored only in backend/.env.demo-host.local; no existing account password is changed.");
        } finally {
            if(testMedia!=null){
                try(var paths=Files.list(testMedia)){
                    for(var path:paths.toList())if(Files.isRegularFile(path)&&path.getParent().equals(testMedia))Files.delete(path);
                }
                Files.delete(testMedia);
            }
        }
    }
}
