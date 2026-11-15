package com.hotel.hotel_booking.media;

import com.hotel.hotel_booking.hotel.ImageStorage;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit maintenance operation; never invoked by an HTTP endpoint or normal startup. */
@Service
public class MediaMigrationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ImageStorage storage;
    private final CloudinaryGateway cloud;
    private final CloudinarySettings settings;

    public MediaMigrationService(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
            ImageStorage storage, CloudinaryGateway cloud, CloudinarySettings settings) {
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(manager); this.storage=storage; this.cloud=cloud; this.settings=settings;
    }

    public record Result(int legacyFiles, int migratedFiles, int failedFiles) {}

    public Result migrate(boolean apply) {
        if (apply && !settings.enabled()) throw new IllegalStateException("Enable cloudinary storage before applying migration.");
        var keys = new TreeSet<String>();
        keys.addAll(jdbc.queryForList("select storage_key from stay_hotel_images", String.class));
        keys.addAll(jdbc.queryForList("select storage_key from stay_account_avatars", String.class));
        keys.removeIf(CloudinaryGateway::cloudKey);
        if (!apply) return new Result(keys.size(), 0, 0);
        int migrated=0, failed=0;
        for (String key : keys) {
            try {
                byte[] original=storage.readLocal(key);
                String replacement="cld-"+key;
                cloud.upload(replacement, original);
                // Do not change a DB reference until the uploaded original can be retrieved intact.
                if (!Arrays.equals(original, cloud.read(replacement))) throw new IllegalStateException("Cloud image verification failed.");
                Integer changed=transactions.execute(tx -> {
                    int hotels=jdbc.update("update stay_hotel_images set storage_key=? where storage_key=?", replacement, key);
                    int avatars=jdbc.update("update stay_account_avatars set storage_key=? where storage_key=?", replacement, key);
                    return hotels+avatars;
                });
                if (changed!=null && changed>0) migrated++;
            } catch (RuntimeException failure) {
                failed++;
                // No remote URLs, signed requests, credentials, or user data in logs.
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("A legacy image could not be migrated. Its local file and DB reference were retained; retry after checking storage.");
            }
        }
        return new Result(keys.size(), migrated, failed);
    }
}
