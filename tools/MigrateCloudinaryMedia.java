import com.hotel.hotel_booking.HotelBookingApplication;
import com.hotel.hotel_booking.media.MediaMigrationService;
import org.springframework.boot.SpringApplication;

/** Run from the backend directory, using its normal runtime classpath. Defaults to dry run. */
public class MigrateCloudinaryMedia {
    public static void main(String[] args) {
        boolean apply=args.length==1 && args[0].equals("--apply");
        if(args.length>1 || (args.length==1 && !apply && !args[0].equals("--dry-run")))
            throw new IllegalArgumentException("Use --dry-run or --apply.");
        int exit=0;
        try(var app=SpringApplication.run(HotelBookingApplication.class,
                "--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.show-sql=false",
                "--logging.level.root=ERROR", "--app.admin.bootstrap.enabled=false",
                "--app.payments.enabled=false", "--app.payments.scheduling-enabled=false")) {
            var result=app.getBean(MediaMigrationService.class).migrate(apply);
            System.out.printf("%s: legacy=%d migrated=%d failed=%d%n", apply?"Applied":"Dry run", result.legacyFiles(), result.migratedFiles(), result.failedFiles());
            if(result.failedFiles()>0)exit=1;
        }
        System.exit(exit);
    }
}
