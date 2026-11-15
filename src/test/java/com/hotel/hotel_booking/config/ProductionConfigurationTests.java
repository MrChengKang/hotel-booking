package com.hotel.hotel_booking.config;

import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationTests {
    @TempDir Path media;
    MockEnvironment env;
    @BeforeEach void settings() {
        env=new MockEnvironment().withProperty("app.frontend-url","https://stay.example.com")
            .withProperty("app.cors.allowed-origins","https://stay.example.com")
            .withProperty("server.servlet.session.cookie.secure","true")
            .withProperty("server.servlet.session.cookie.same-site","lax")
            .withProperty("spring.jpa.hibernate.ddl-auto","validate")
            .withProperty("app.auth.remember-key","test-only-production-settings-key-at-least-32")
            .withProperty("spring.datasource.url","jdbc:mysql://db.example.com:3306/defaultdb?sslMode=REQUIRED")
            .withProperty("spring.datasource.username","test-user")
            .withProperty("spring.datasource.password","test-password")
            .withProperty("app.payments.enabled","false")
            .withProperty("app.media.directory",media.toString());
    }
    @Test void acceptsValidSameOriginDeploymentSettings() {assertDoesNotThrow(()->ProductionConfiguration.validate(env));}
    @Test void rejectsDevelopmentAndAmbiguousFrontendOrigins() {
        for(String value:new String[]{"http://stay.example.com","https://localhost","https://stay.example.com/","https://stay.example.com/path","https://stay.example.com?q=1","https://*.example.com","https://user:pass@stay.example.com"}) {
            env.setProperty("app.frontend-url",value);
            assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        }
    }
    @Test void requiresTheFrontendInExplicitCorsOrigins() {
        env.setProperty("app.cors.allowed-origins","https://different.example.com");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        env.setProperty("app.cors.allowed-origins","*");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void neverRunsProductionWithLocalSecretImports() {
        env.setActiveProfiles("local","prod");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void requiresSecureCookiesAndKnownSameSitePolicy() {
        env.setProperty("server.servlet.session.cookie.secure","false");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        env.setProperty("server.servlet.session.cookie.secure","true");
        env.setProperty("server.servlet.session.cookie.same-site","none");
        assertDoesNotThrow(()->ProductionConfiguration.validate(env));
        env.setProperty("server.servlet.session.cookie.same-site","unknown");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void preventsImplicitSchemaChangesAndEphemeralRememberKeys() {
        env.setProperty("spring.jpa.hibernate.ddl-auto","update");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        env.setProperty("spring.jpa.hibernate.ddl-auto","validate");
        env.setProperty("app.auth.remember-key","");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void requiresEncryptedMysqlWithoutDuplicateTlsModes() {
        for(String query:new String[]{"","?sslMode=DISABLED","?sslMode=PREFERRED","?sslMode=REQUIRED&sslMode=DISABLED"}) {
            env.setProperty("spring.datasource.url","jdbc:mysql://db.example.com/defaultdb"+query);
            assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        }
        env.setProperty("spring.datasource.url","jdbc:mysql://db.example.com/defaultdb?sslMode=VERIFY_IDENTITY");
        assertDoesNotThrow(()->ProductionConfiguration.validate(env));
    }
    @Test void requiresAnAbsoluteWritableMediaDirectory() {
        env.setProperty("app.media.directory","uploads/hotel-images");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void requiresConfiguredSandboxKeysWhenPaymentsAreEnabled() {
        env.setProperty("app.payments.enabled","true");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        env.setProperty("app.payments.stripe-secret","sk_test_only_for_unit_tests");
        env.setProperty("app.payments.webhook-secret","whsec_only_for_unit_tests");
        assertDoesNotThrow(()->ProductionConfiguration.validate(env));
        env.setProperty("app.payments.stripe-secret","sk_live_never_allowed");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
    }
    @Test void cloudModeRequiresCredentialsButNoLocalPersistentDisk() {
        env.setProperty("app.media.provider","cloudinary");
        env.setProperty("app.media.directory","unused-relative-path");
        assertThrows(IllegalStateException.class,()->ProductionConfiguration.validate(env));
        env.setProperty("app.media.cloudinary.cloud-name","test-cloud");
        env.setProperty("app.media.cloudinary.api-key","test-key");
        env.setProperty("app.media.cloudinary.api-secret","test-secret");
        assertDoesNotThrow(()->ProductionConfiguration.validate(env));
    }
}
