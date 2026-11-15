package com.hotel.hotel_booking.media;

import com.hotel.hotel_booking.hotel.ImageStorage;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaMigrationTests {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    CloudinaryGateway cloud;
    MediaMigrationService migration;
    String key="12345678-1234-1234-1234-123456789abc.png";
    byte[] original={1,2,3};

    @BeforeEach void setup() throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:migrate"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(data);
        jdbc.execute("create table stay_hotel_images (id bigint primary key, storage_key varchar(50))");
        jdbc.execute("create table stay_account_avatars (user_id bigint primary key, storage_key varchar(50))");
        jdbc.update("insert into stay_hotel_images values (1,?)",key);
        jdbc.update("insert into stay_account_avatars values (2,?)",key);
        Files.write(directory.resolve(key),original);
        cloud=mock(CloudinaryGateway.class);
        when(cloud.read("cld-"+key)).thenReturn(original);
        var settings=new CloudinarySettings(CloudinaryGatewayTests.environment());
        migration=new MediaMigrationService(jdbc,new DataSourceTransactionManager(data),new ImageStorage(directory.toString(),settings,cloud),cloud,settings);
    }
    @Test void dryRunDoesNotUploadOrChangeReferences() {
        assertEquals(new MediaMigrationService.Result(1,0,0),migration.migrate(false));
        verifyNoInteractions(cloud);assertEquals(key,jdbc.queryForObject("select storage_key from stay_hotel_images",String.class));
    }
    @Test void verifiesBytesBeforeAtomicUpdatesAndCanBeRerun() {
        assertEquals(new MediaMigrationService.Result(1,1,0),migration.migrate(true));
        assertEquals("cld-"+key,jdbc.queryForObject("select storage_key from stay_hotel_images",String.class));
        assertEquals("cld-"+key,jdbc.queryForObject("select storage_key from stay_account_avatars",String.class));
        assertArrayEquals(original,assertDoesNotThrow(()->Files.readAllBytes(directory.resolve(key))));
        assertEquals(new MediaMigrationService.Result(0,0,0),migration.migrate(true));
        verify(cloud,times(1)).upload("cld-"+key,original);verify(cloud,never()).delete(anyString());
    }
    @Test void failedVerificationRetainsFilesAndReferences() {
        when(cloud.read("cld-"+key)).thenReturn(new byte[]{4});
        assertEquals(1,migration.migrate(true).failedFiles());
        assertEquals(key,jdbc.queryForObject("select storage_key from stay_hotel_images",String.class));
        assertTrue(Files.exists(directory.resolve(key)));
    }
    @Test void databaseFailureRollsBackBothReferenceUpdates() {
        jdbc.execute("alter table stay_account_avatars alter column storage_key varchar(40)");
        assertEquals(1,migration.migrate(true).failedFiles());
        assertEquals(key,jdbc.queryForObject("select storage_key from stay_hotel_images",String.class));
        assertEquals(key,jdbc.queryForObject("select storage_key from stay_account_avatars",String.class));
    }
    @Test void missingLocalFileDoesNotCreateCloudOrDatabaseChanges() throws Exception {
        Files.delete(directory.resolve(key));assertEquals(1,migration.migrate(true).failedFiles());
        verifyNoInteractions(cloud);assertEquals(key,jdbc.queryForObject("select storage_key from stay_hotel_images",String.class));
    }
}
