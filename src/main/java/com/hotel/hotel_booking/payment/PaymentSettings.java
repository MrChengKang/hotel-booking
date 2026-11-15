package com.hotel.hotel_booking.payment;
import java.net.URI;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentSettings {
    public final boolean enabled;public final String secret,webhookSecret,frontend;
    public PaymentSettings(@Value("${app.payments.enabled:true}") boolean enabled,@Value("${app.payments.stripe-secret:}") String secret,@Value("${app.payments.webhook-secret:}") String webhookSecret,@Value("${app.frontend-url:http://localhost:3000}") String frontend){
        this.enabled=enabled;this.secret=secret.strip();this.webhookSecret=webhookSecret.strip();this.frontend=frontend.replaceAll("/+$","");
        var uri=URI.create(this.frontend);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!("https".equals(uri.getScheme())||("http".equals(uri.getScheme())&&Set.of("localhost","127.0.0.1").contains(uri.getHost()))))throw new IllegalArgumentException("Use an HTTPS frontend URL or local development URL.");
        if(!this.secret.isEmpty()&&!this.secret.startsWith("sk_test_")&&!this.secret.startsWith("rk_test_"))throw new IllegalArgumentException("STAY payments accept Stripe sandbox keys only.");
    }
    public boolean ready(){return enabled&&(secret.startsWith("sk_test_")||secret.startsWith("rk_test_"))&&webhookSecret.startsWith("whsec_");}
    public void requireReady(){if(!ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Test payments are not configured yet. Set the backend Stripe sandbox keys.");}
    public static long minor(BigDecimal amount,String currency){
        int digits=switch(currency){case "MYR","USD","EUR","GBP","SGD","AUD","CAD" -> 2;case "JPY" -> 0;default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Online payment currently supports MYR, USD, EUR, GBP, SGD, AUD, CAD and JPY.");};
        try{long value=amount.movePointRight(digits).longValueExact();if(value<0||value>99_999_999)throw new ArithmeticException();return value;}catch(ArithmeticException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The booking total cannot be represented by the payment provider.");}
    }
}
