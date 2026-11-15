import com.hotel.hotel_booking.mail.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.core.env.*;
import tools.jackson.databind.ObjectMapper;

/** Explicit local maintenance command; never called on application startup. No database access. */
public class SendBrevoTestEmail {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Specify your own test recipient email address.");
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(Path.of(".env.brevo.local"))){properties.load(reader);}
        if(!"brevo".equals(properties.getProperty("STAY_MAIL_PROVIDER", "").strip()))
            throw new IllegalStateException("Set STAY_MAIL_PROVIDER=brevo in .env.brevo.local first.");
        Map<String,Object> values=new HashMap<>();
        values.put("app.mail.brevo.api-key",properties.getProperty("BREVO_API_KEY",""));
        values.put("app.mail.brevo.sender-email",properties.getProperty("BREVO_SENDER_EMAIL",""));
        values.put("app.mail.brevo.sender-name",properties.getProperty("BREVO_SENDER_NAME","STAY"));
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("private-brevo",values));
        var client=new BrevoMailClient(new BrevoMailSettings(env),new ObjectMapper());
        client.sendText(args[0],"STAY email connection test",
            "This is a connection test for the STAY hotel booking project.\n\n"
            +"No password reset was requested and no account password was changed.\n"
            +"This message does not contain a password reset link. No action is required.\n\nSTAY");
        System.out.println("Brevo accepted the test email (HTTP 201). Confirm receipt in your inbox/spam folder and Brevo Transactional logs.");
    }
}
