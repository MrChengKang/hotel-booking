package com.hotel.hotel_booking.auth;

import com.hotel.hotel_booking.mail.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailConfigurationTests {
    ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(MailConfiguration.class)
            .withBean(ObjectMapper.class,ObjectMapper::new).withBean(JavaMailSender.class,()->mock(JavaMailSender.class));
    @Test void existingSmtpRemainsTheDefault() {
        runner.withPropertyValues("spring.mail.username=sender@example.com","spring.mail.password=test-only")
                .run(ctx->{assertInstanceOf(GmailResetMailSender.class,ctx.getBean(ResetMailSender.class));assertTrue(ctx.getBean(ResetMailSender.class).available());});
    }
    @Test void brevoSelectionDoesNotFallBackToConfiguredSmtp() {
        runner.withPropertyValues("app.mail.provider=brevo","spring.mail.username=sender@example.com","spring.mail.password=test-only")
                .run(ctx->{assertInstanceOf(BrevoResetMailSender.class,ctx.getBean(ResetMailSender.class));assertFalse(ctx.getBean(ResetMailSender.class).available());});
    }
    @Test void completeBrevoConfigurationIsAvailableWithoutSmtp() {
        runner.withPropertyValues("app.mail.provider=brevo","app.mail.brevo.api-key=test-only","app.mail.brevo.sender-email=sender@example.com")
                .run(ctx->{assertInstanceOf(BrevoResetMailSender.class,ctx.getBean(ResetMailSender.class));assertTrue(ctx.getBean(ResetMailSender.class).available());});
    }
    @Test void unknownProviderFailsClearly() {
        runner.withPropertyValues("app.mail.provider=typo").run(ctx->assertNotNull(ctx.getStartupFailure()));
    }
    @Test void resetMailKeepsTheSingleUseFragmentLinkInText() {
        var client=mock(BrevoMailClient.class);when(client.available()).thenReturn(true);
        var sender=new BrevoResetMailSender(client);assertTrue(sender.available());
        String link="https://stay.example.com/reset-password#token=test-private-token";
        sender.send("guest@example.com",link);
        verify(client).sendText("guest@example.com",ResetEmailContent.SUBJECT,ResetEmailContent.text(link));
        assertTrue(ResetEmailContent.text(link).contains("15 minutes"));
        assertTrue(ResetEmailContent.text(link).contains("only once"));
    }
}
