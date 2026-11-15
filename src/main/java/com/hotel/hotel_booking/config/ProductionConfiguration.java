package com.hotel.hotel_booking.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfiguration {
    @Bean InitializingBean productionSettingsValidator(Environment environment) {
        return () -> validate(environment);
    }

    static void validate(Environment env) throws Exception {
        if (Arrays.asList(env.getActiveProfiles()).contains("local"))
            throw invalid("Do not combine prod and local profiles.");
        String frontend=origin(required(env,"app.frontend-url"));
        var origins=Arrays.stream(required(env,"app.cors.allowed-origins").split(","))
            .map(String::strip).map(ProductionConfiguration::origin).toList();
        if(!origins.contains(frontend))throw invalid("CORS origins must include APP_FRONTEND_URL.");
        if(!Boolean.parseBoolean(required(env,"server.servlet.session.cookie.secure")))
            throw invalid("Production session cookies require HTTPS and Secure=true.");
        if(!Set.of("lax","strict","none").contains(required(env,"server.servlet.session.cookie.same-site").toLowerCase(Locale.ROOT)))
            throw invalid("SESSION_COOKIE_SAME_SITE must be lax, strict or none.");
        if(!"validate".equals(required(env,"spring.jpa.hibernate.ddl-auto")))
            throw invalid("Production schema mode must be validate; apply schema changes explicitly.");
        if(required(env,"app.auth.remember-key").length()<32)
            throw invalid("Configure a stable STAY_REMEMBER_ME_KEY with at least 32 characters.");
        String database=required(env,"spring.datasource.url");
        if(!database.startsWith("jdbc:mysql:"))throw invalid("Production requires the configured MySQL database.");
        var query=URI.create(database.substring(5)).getRawQuery();
        var modes=query==null?List.<String>of():Arrays.stream(query.split("&"))
            .map(pair->pair.split("=",2)).filter(pair->pair.length==2 && pair[0].equalsIgnoreCase("sslMode"))
            .map(pair->URLDecoder.decode(pair[1],StandardCharsets.UTF_8).toUpperCase(Locale.ROOT)).toList();
        if(modes.size()!=1 || !Set.of("REQUIRED","VERIFY_CA","VERIFY_IDENTITY").contains(modes.getFirst()))
            throw invalid("Set JDBC sslMode to REQUIRED, VERIFY_CA or VERIFY_IDENTITY.");
        required(env,"spring.datasource.username");required(env,"spring.datasource.password");
        if(Boolean.parseBoolean(env.getProperty("app.payments.enabled","true"))) {
            String secret=required(env,"app.payments.stripe-secret");
            if(!(secret.startsWith("sk_test_")||secret.startsWith("rk_test_")) || !required(env,"app.payments.webhook-secret").startsWith("whsec_"))
                throw invalid("Configure Stripe sandbox keys or disable test payments.");
        }
        String provider=env.getProperty("app.media.provider","local");
        if(provider.equals("cloudinary")) {
            // Cloud mode does not require a writable permanent local disk.
            new com.hotel.hotel_booking.media.CloudinarySettings(env).requireReady();
            return;
        }
        if(!provider.equals("local"))throw invalid("STAY_MEDIA_PROVIDER must be local or cloudinary.");
        Path media=Path.of(required(env,"app.media.directory"));
        if(!media.isAbsolute())throw invalid("STAY_MEDIA_DIRECTORY must be an absolute persistent storage path.");
        Files.createDirectories(media);
        if(!Files.isDirectory(media)||!Files.isWritable(media))throw invalid("The media directory must be writable by the application user.");
    }

    private static String required(Environment env,String key) {
        String value=env.getProperty(key);
        if(value==null||value.isBlank())throw invalid("Missing required production setting: "+key);
        return value.strip();
    }
    private static String origin(String value) {
        URI uri;
        try {uri=URI.create(value);}catch(IllegalArgumentException failure){throw invalid("Use explicit HTTPS origins without paths or wildcards.");}
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getHost().contains("*")||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
            ||(uri.getPath()!=null&&!uri.getPath().isEmpty())||Set.of("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost()))
            throw invalid("Use explicit public HTTPS origins without paths or wildcards.");
        return uri.toString();
    }
    private static IllegalStateException invalid(String message) {return new IllegalStateException(message);}
}
