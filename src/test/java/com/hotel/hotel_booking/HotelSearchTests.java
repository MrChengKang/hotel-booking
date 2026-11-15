package com.hotel.hotel_booking;

import com.hotel.hotel_booking.hotel.*;
import com.hotel.hotel_booking.user.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static com.hotel.hotel_booking.hotel.HotelDtos.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class HotelSearchTests {
    @Autowired HotelRepository hotels;
    @Autowired RoomTypeRepository rooms;
    @Autowired RoomInventoryRepository inventory;
    @Autowired UserRepository users;
    @Autowired HotelSearchService search;
    @Autowired HotelManagementService management;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @LocalServerPort int port;
    User host;
    int serial;
    LocalDate from = LocalDate.now(ZoneOffset.UTC).plusDays(30);

    @BeforeEach void setup() {
        host = users.save(new User("Search Host", "search-tests@example.com", "hash", Role.HOST));
    }
    Hotel hotel(String currency, HotelAmenity... amenities) {
        var hotel = new Hotel(host,"Search Hotel " + (++serial),"search-hotel-" + serial,"Search fixture","1 Test Road","Search City","MY",currency,"Asia/Kuala_Lumpur");
        hotel.editAmenities(List.of(amenities)); hotel.publish(); return hotels.saveAndFlush(hotel);
    }
    RoomType room(Hotel hotel, int guests, int beds, int quantity, String base, String... nightly) {
        var room = rooms.saveAndFlush(new RoomType(hotel,"R" + (++serial),"Test Room","",guests,beds,quantity,new BigDecimal(base)));
        for (int i = 0; i < nightly.length; i++) inventory.save(new RoomInventory(room,from.plusDays(i),quantity,new BigDecimal(nightly[i])));
        em.flush(); return room;
    }
    PageResponse<HotelView> find(LocalDate in,LocalDate out,int quantity,int guests,int beds,BigDecimal min,BigDecimal max,String currency,String order,List<HotelAmenity> amenities,int page,int size) {
        em.flush();
        return search.search("search city",in,out,quantity,guests,beds,min,max,currency,order,amenities,page,size);
    }
    PageResponse<HotelView> dated() { return find(from,from.plusDays(2),1,2,0,null,null,null,"newest",List.of(),0,12); }

    @Test void filtersAvailabilityBeforePaginationAndCountsAllMatchingHotels() {
        var available = hotel("MYR"); room(available,2,1,2,"100","100","100");
        var alsoAvailable = hotel("MYR"); room(alsoAvailable,2,1,2,"200","200","200");
        for (int i=0;i<14;i++) { var unavailable=hotel("MYR");room(unavailable,2,1,2,"1","1"); }
        var first=find(from,from.plusDays(2),1,2,0,null,null,null,"newest",List.of(),0,1);
        assertEquals(2,first.totalElements());assertEquals(2,first.totalPages());
        assertEquals(alsoAvailable.getId(),first.items().getFirst().id());
        var second=find(from,from.plusDays(2),1,2,0,null,null,null,"newest",List.of(),1,1);
        assertEquals(available.getId(),second.items().getFirst().id());
        assertTrue(find(from,from.plusDays(2),1,2,0,null,null,null,"newest",List.of(),2,1).items().isEmpty());
    }
    @Test void everyNightAndSameRoomTypeMustMatch() {
        var hotel=hotel("MYR");
        room(hotel,2,1,2,"100","100");
        var second=room(hotel,2,1,2,"100");
        inventory.saveAndFlush(new RoomInventory(second,from.plusDays(1),2,new BigDecimal("100")));
        assertEquals(0,dated().totalElements());
    }
    @Test void checkoutIsExcludedAndInventoryPricesOverrideBaseRate() {
        var hotel=hotel("MYR");var room=room(hotel,2,1,2,"1","100","200");
        var closed=new RoomInventory(room,from.plusDays(2),2,new BigDecimal("999"));closed.configure(2,0,new BigDecimal("999"),true);inventory.saveAndFlush(closed);
        var result=dated().items().getFirst();
        assertEquals(0,new BigDecimal("150").compareTo(result.startingNightlyPrice()));
        assertEquals(room.getId(),result.roomTypeId());
    }
    @Test void blockedBookedAndStopSellExcludeOffers() {
        var hotel=hotel("MYR");var room=room(hotel,2,1,2,"100","100","100");
        var first=inventory.findByRoomTypeIdAndStayDate(room.getId(),from).orElseThrow();
        first.reserve(1);first.configure(2,1,new BigDecimal("100"),false);
        assertEquals(0,dated().totalElements());
        first.configure(2,0,new BigDecimal("100"),true);
        assertEquals(0,dated().totalElements());
    }
    @Test void allRequestedAmenitiesAreRequiredAndNoDuplicatesAppear() {
        var both=hotel("MYR",HotelAmenity.WIFI,HotelAmenity.POOL);room(both,2,1,2,"100","100","100");room(both,2,1,2,"120","120","120");
        var partial=hotel("MYR",HotelAmenity.WIFI);room(partial,2,1,2,"90","90","90");
        var result=find(from,from.plusDays(2),1,2,0,null,null,null,"newest",List.of(HotelAmenity.WIFI,HotelAmenity.POOL),0,12);
        assertEquals(1,result.totalElements());assertEquals(both.getId(),result.items().getFirst().id());
        assertEquals(2,result.items().getFirst().amenities().size());
    }
    @Test void priceCapacityAndBedsMustMatchTheSameOffer() {
        var hotel=hotel("MYR");room(hotel,1,1,3,"50","50","50");room(hotel,4,2,3,"300","300","300");
        assertEquals(0,find(from,from.plusDays(2),1,4,2,null,new BigDecimal("100"),"MYR","newest",List.of(),0,12).totalElements());
        var result=find(from,from.plusDays(2),2,7,2,null,new BigDecimal("300"),"MYR","newest",List.of(),0,12);
        assertEquals(1,result.totalElements());assertEquals(0,new BigDecimal("300").compareTo(result.items().getFirst().startingNightlyPrice()));
    }
    @Test void currenciesNeverMixAndBothPriceSortsUseAvailableAverage() {
        var expensive=hotel("MYR");room(expensive,2,1,2,"1","200","400");
        var cheap=hotel("MYR");room(cheap,2,1,2,"999","100","200");
        var foreign=hotel("USD");room(foreign,2,1,2,"1","1","1");
        assertEquals(List.of(cheap.getId(),expensive.getId()),find(from,from.plusDays(2),1,2,0,null,null,"MYR","price_asc",List.of(),0,12).items().stream().map(HotelView::id).toList());
        assertEquals(expensive.getId(),find(from,from.plusDays(2),1,2,0,null,null,"MYR","price_desc",List.of(),0,12).items().getFirst().id());
        assertEquals(1,find(from,from.plusDays(2),1,2,0,new BigDecimal("150"),new BigDecimal("150"),"MYR","price_asc",List.of(),0,12).totalElements());
    }
    @Test void undatedSearchUsesBaseRatesAndExcludesDraftArchivedAndInactive() {
        var visible=hotel("MYR");room(visible,2,1,2,"80");
        var draft=hotel("MYR");room(draft,2,1,2,"1");draft.unpublish();
        var archived=hotel("MYR");room(archived,2,1,2,"1");archived.archive();
        var inactive=hotel("MYR");room(inactive,2,1,2,"1").deactivate();
        var result=find(null,null,1,2,0,null,null,"MYR","price_asc",List.of(),0,12);
        assertEquals(1,result.totalElements());assertEquals(visible.getId(),result.items().getFirst().id());
        assertEquals(0,new BigDecimal("80").compareTo(result.items().getFirst().startingNightlyPrice()));
    }
    @Test void pastStaysAreExcludedUsingHotelTimeZone() {
        var hotel=hotel("MYR");room(hotel,2,1,2,"100","100","100");
        var yesterday=LocalDate.now(ZoneId.of(hotel.getTimeZone())).minusDays(1);
        assertEquals(0,find(yesterday,yesterday.plusDays(1),1,2,0,null,null,null,"newest",List.of(),0,12).totalElements());
    }
    @Test void invalidFiltersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->find(from,null,1,2,0,null,null,null,"newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(from,from,1,2,0,null,null,null,"newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,0,2,0,null,null,null,"newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,1,2,0,null,null,null,"price_asc",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,1,2,0,new BigDecimal("10"),new BigDecimal("1"),"MYR","newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,1,2,0,new BigDecimal("0.001"),null,"MYR","newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,1,2,0,null,null,"XXXZ","newest",List.of(),0,12));
        assertThrows(IllegalArgumentException.class,()->find(null,null,1,2,0,null,null,null,"id desc; drop table x",List.of(),0,12));
    }
    @Test void amenityEditingPreservesLegacyClientsAndRequiresNewReview() {
        var hotel=hotel("MYR",HotelAmenity.WIFI);
        management.edit(hotel.getId(),host.getEmail(),new HotelEdit(hotel.getName(),hotel.getDescription(),hotel.getAddress(),hotel.getCity()));
        assertEquals(HotelStatus.PUBLISHED,hotel.getStatus());assertTrue(hotel.getAmenities().contains(HotelAmenity.WIFI));
        management.edit(hotel.getId(),host.getEmail(),new HotelEdit(hotel.getName(),hotel.getDescription(),hotel.getAddress(),hotel.getCity(),List.of(HotelAmenity.POOL)));
        assertEquals(HotelStatus.DRAFT,hotel.getStatus());assertEquals(Set.of(HotelAmenity.POOL),hotel.getAmenities());
        hotel.submitForReview();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->management.edit(hotel.getId(),host.getEmail(),new HotelEdit(hotel.getName(),hotel.getDescription(),hotel.getAddress(),hotel.getCity(),List.of())));
    }
    @Test void hotelOwnerCannotEditAnotherHostsAmenities() {
        var hotel=hotel("MYR",HotelAmenity.WIFI);
        users.saveAndFlush(new User("Other Host","search-other@example.com","hash",Role.HOST));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->management.edit(hotel.getId(),"search-other@example.com",new HotelEdit(hotel.getName(),hotel.getDescription(),hotel.getAddress(),hotel.getCity(),List.of(HotelAmenity.POOL))));
        assertEquals(Set.of(HotelAmenity.WIFI),hotel.getAmenities());
    }
    @Test void amenitiesTableHasRealCompositePrimaryKeyRequiredByAiven() {
        var primary = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Set<String>>) connection -> {
            Set<String> columns = new HashSet<>();
            try (var keys = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, "STAY_HOTEL_AMENITIES")) {
                while (keys.next()) columns.add(keys.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            return columns;
        });
        assertEquals(Set.of("hotel_id", "amenity"), primary);
    }
    @Test void overlappingAmenityChangesPreserveExistingEntriesAndPersistAdditionsAndRemovals() {
        var hotel=hotel("MYR",HotelAmenity.WIFI);
        hotel.editAmenities(List.of(HotelAmenity.WIFI,HotelAmenity.POOL));em.flush();em.clear();
        var loaded=hotels.findById(hotel.getId()).orElseThrow();
        assertEquals(Set.of(HotelAmenity.WIFI,HotelAmenity.POOL),loaded.getAmenities());
        loaded.editAmenities(List.of(HotelAmenity.POOL));em.flush();em.clear();
        assertEquals(Set.of(HotelAmenity.POOL),hotels.findById(hotel.getId()).orElseThrow().getAmenities());
    }
    @Test void publicApiRejectsUnknownAmenitiesAndIncompleteDates() throws Exception {
        var client=HttpClient.newHttpClient();
        var unfiltered=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/hotels")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,unfiltered.statusCode(),unfiltered.body());
        for (String query : List.of("amenities=FAKE_POOL", "checkIn=2027-01-01", "minPrice=10", "sort=price_asc", "rooms=0")) {
            var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/hotels?"+query)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(400,response.statusCode(),response.body());
        }
    }
}
