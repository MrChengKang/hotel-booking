package com.hotel.hotel_booking;

import com.hotel.hotel_booking.payment.*;
import com.hotel.hotel_booking.booking.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

// Run the existing payment regression tests against a context that also processes refunds.
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:refund;MODE=MySQL;DB_CLOSE_DELAY=-1", "app.payments.enabled=true",
    "app.payments.stripe-secret=sk_test_fixture_only", "app.payments.webhook-secret=whsec_fixture_only",
    "app.payments.scheduling-enabled=false"})
@Import({PaymentApiTests.GatewayConfig.class,RefundApiTests.RefundConfig.class})
class RefundApiTests extends PaymentApiTests {
    @TestConfiguration static class RefundConfig { @Bean @Primary FakeRefund fakeRefund(){return new FakeRefund();} }
    static class FakeRefund implements RefundGateway {
        final Map<String,Refund> objects=new ConcurrentHashMap<>();
        final Map<String,String> keys=new ConcurrentHashMap<>();
        final AtomicInteger created=new AtomicInteger();
        volatile boolean unavailable,ambiguous;
        volatile String initial="pending";
        void reset(){objects.clear();keys.clear();created.set(0);unavailable=false;ambiguous=false;initial="pending";}
        ResponseStatusException down(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Test refund unavailable; rooms retained.");}
        public Refund create(Request r){
            if(unavailable)throw down();
            String id=keys.computeIfAbsent(r.key(),key->{created.incrementAndGet();String value="re_"+key.replace("-","");objects.put(value,new Refund(value,r.paymentIntent(),r.amount(),r.currency().toLowerCase(Locale.ROOT),initial,key,r.bookingId().toString(),"STAY"));return value;});
            if(ambiguous){ambiguous=false;throw down();}return objects.get(id);
        }
        public Refund retrieve(String id){if(unavailable)throw down();return Objects.requireNonNull(objects.get(id));}
        void status(String id,String status){var r=objects.get(id);objects.put(id,new Refund(r.id(),r.paymentIntent(),r.amount(),r.currency(),status,r.key(),r.bookingId(),r.app()));}
        String first(){return objects.keySet().iterator().next();}
    }
    @Autowired RefundAttemptRepository refunds;
    @Autowired RefundService refundService;
    @Autowired FakeRefund refund;
    @Override void clean(){if(refunds!=null)refunds.deleteAllInBatch();if(refund!=null)refund.reset();super.clean();}
    long paid()throws Exception{long b=book();checkout(b);stripe.pay(stripe.first());assertEquals(204,webhook("evt_original_paid").statusCode());return b;}
    java.net.http.HttpResponse<String> requestRefund(long b)throws Exception{return guest.send("POST",path(b)+"/refund","{\"expectedAmount\":200.00}");}
    java.net.http.HttpResponse<String> refundEvent(String eventId)throws Exception{
        String body="{\"id\":\""+eventId+"\",\"type\":\"refund.updated\",\"livemode\":false,\"data\":{\"object\":{\"id\":\""+refund.first()+"\",\"object\":\"refund\"}}}";
        return webhook(body,signature(body,Instant.now().getEpochSecond()));
    }
    @Test void successCancelsAndReleasesOnlyOnceEvenWithDuplicateCheckoutAndRefundEvents()throws Exception{
        long b=paid();refund.initial="succeeded";
        assertTrue(guest.send("GET",path(b)+"/cancellation",null).body().contains("\"allowed\":true"));
        assertEquals(202,requestRefund(b).statusCode());assertEquals(0,reserved());assertEquals("REFUNDED",bookings.findById(b).orElseThrow().getPaymentStatus());
        assertEquals(BookingStatus.CANCELLED,bookings.findById(b).orElseThrow().getStatus());
        long notices=notifications.count();assertEquals(202,requestRefund(b).statusCode());assertEquals(1,refund.created.get());
        assertEquals(204,refundEvent("evt_refund_once").statusCode());assertEquals(204,refundEvent("evt_refund_once").statusCode());
        assertEquals(204,webhook("evt_late_checkout").statusCode());assertEquals(0,reserved());assertEquals(notices,notifications.count());
        assertEquals("REFUNDED",bookings.findById(b).orElseThrow().getPaymentStatus());
    }
    @Test void pendingRefundKeepsRoomsAndCannotBypassTheRefundThroughUnpaidCancellation()throws Exception{
        long b=paid();assertEquals(202,requestRefund(b).statusCode());assertEquals(2,reserved());
        assertEquals("REFUND_PENDING",bookings.findById(b).orElseThrow().getPaymentStatus());
        assertEquals(409,guest.send("POST",path(b)+"/cancel",null).statusCode());assertEquals(2,reserved());
        refund.status(refund.first(),"succeeded");assertEquals(204,refundEvent("evt_done").statusCode());assertEquals(0,reserved());
    }
    @Test void ownershipCsrfAndServerQuoteAreEnforcedBeforeAnyRefund()throws Exception{
        long b=paid();
        assertEquals(404,other.send("GET",path(b)+"/cancellation",null).statusCode());
        assertEquals(404,other.send("POST",path(b)+"/refund","{\"expectedAmount\":200}").statusCode());
        assertEquals(403,host.send("POST",path(b)+"/refund","{\"expectedAmount\":200}").statusCode());
        assertEquals(403,guest.send("POST",path(b)+"/refund","{\"expectedAmount\":200}",false).statusCode());
        assertEquals(409,guest.send("POST",path(b)+"/refund","{\"expectedAmount\":1}").statusCode());
        assertEquals(400,guest.send("POST",path(b)+"/refund","{}").statusCode());
        assertEquals(0,refunds.count());assertEquals(0,refund.created.get());assertEquals(2,reserved());
    }
    @Test void unpaidAndLocalCheckInDayCannotInitiateRefunds()throws Exception{
        long b=book();assertEquals(409,requestRefund(b).statusCode());checkout(b);stripe.pay(stripe.first());webhook("evt_pay");
        jdbc.update("update stay_bookings set check_in=? where id=?",LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")),b);
        assertEquals(409,requestRefund(b).statusCode());assertEquals(0,refunds.count());assertEquals(2,reserved());
    }
    @Test void ambiguousCreateAndConcurrentRequestsReuseTheDurableRefundKey()throws Exception{
        long b=paid();refund.ambiguous=true;assertEquals(503,requestRefund(b).statusCode());assertEquals(1,refund.created.get());assertEquals(2,reserved());
        var second=login("paymentguest@example.com");
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->requestRefund(b));var c=pool.submit(()->second.send("POST",path(b)+"/refund","{\"expectedAmount\":200}"));
            assertEquals(202,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(202,c.get(25,TimeUnit.SECONDS).statusCode());
        }
        assertEquals(1,refund.created.get());assertEquals(1,refunds.count());assertEquals(2,reserved());
        refund.status(refund.first(),"succeeded");
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->refundEvent("evt_parallel_refund"));var c=pool.submit(()->refundEvent("evt_parallel_refund"));
            assertEquals(204,a.get(25,TimeUnit.SECONDS).statusCode());assertEquals(204,c.get(25,TimeUnit.SECONDS).statusCode());
        }
        assertEquals(0,reserved());
    }
    @Test void failedRefundRetainsPaidReservationAndDoesNotCreateAnotherRefundOnRetry()throws Exception{
        long b=paid();requestRefund(b);refund.status(refund.first(),"failed");assertEquals(204,refundEvent("evt_failed_refund").statusCode());
        assertEquals("REFUND_FAILED",bookings.findById(b).orElseThrow().getPaymentStatus());assertEquals(BookingStatus.CONFIRMED,bookings.findById(b).orElseThrow().getStatus());
        assertEquals(2,reserved());long notices=notifications.count();assertEquals(202,requestRefund(b).statusCode());
        assertEquals(204,refundEvent("evt_failed_again").statusCode());assertEquals(notices,notifications.count());assertEquals(1,refund.created.get());
        assertEquals(409,guest.send("POST",path(b)+"/cancel",null).statusCode());
    }
    @Test void mismatchedCanonicalRefundCannotCancelOrReleaseInventory()throws Exception{
        long b=paid();requestRefund(b);var r=refund.objects.get(refund.first());
        refund.objects.put(r.id(),new RefundGateway.Refund(r.id(),r.paymentIntent(),1,r.currency(),"succeeded",r.key(),r.bookingId(),r.app()));
        assertEquals(503,refundEvent("evt_mismatch_refund").statusCode());assertEquals(2,reserved());
        assertEquals("REFUND_PENDING",bookings.findById(b).orElseThrow().getPaymentStatus());assertFalse(events.existsById("evt_mismatch_refund"));
    }
    @Test void missedWebhookIsReconciledButUnknownRequestsOlderThanIdempotencyWindowAreNotRecreated()throws Exception{
        long b=paid();requestRefund(b);refund.status(refund.first(),"succeeded");refundService.reconcile(b);assertEquals(0,reserved());
    }
    @Test void oldAmbiguousRequestNeedsReviewAndKeepsItsRooms()throws Exception{
        long b=paid();refund.ambiguous=true;requestRefund(b);
        jdbc.update("update stay_refund_attempts set created_at=? where booking_id=?",Instant.now().minusSeconds(24*3600),b);
        refundService.reconcile(b);assertEquals("REVIEW_REQUIRED",refunds.findByBookingId(b).orElseThrow().getStatus());assertEquals(1,refund.created.get());assertEquals(2,reserved());
        refund.status(refund.first(),"succeeded");assertEquals(204,refundEvent("evt_late_discovery").statusCode());assertEquals(0,reserved());
    }
    @Test void requiresActionIsManualReviewAndCannotDuplicateNotificationsOnStateCycles()throws Exception{
        long b=paid();requestRefund(b);refund.status(refund.first(),"requires_action");refundEvent("evt_action");
        assertEquals("REVIEW_REQUIRED",refunds.findByBookingId(b).orElseThrow().getStatus());long notices=notifications.count();
        refund.status(refund.first(),"pending");refundEvent("evt_pending_again");refund.status(refund.first(),"requires_action");refundEvent("evt_action_again");
        assertEquals(notices,notifications.count());assertEquals(2,reserved());
    }
    @Test void deadlineUsesHotelTimeZoneAndIsExclusive(){
        LocalDate date=LocalDate.of(2030,1,2);
        assertEquals(Instant.parse("2030-01-01T16:00:00Z"),CancellationPolicy.deadline(date,"Asia/Kuala_Lumpur"));
        assertTrue(CancellationPolicy.beforeCheckIn(Instant.parse("2030-01-01T15:59:59Z"),date,"Asia/Kuala_Lumpur"));
        assertFalse(CancellationPolicy.beforeCheckIn(Instant.parse("2030-01-01T16:00:00Z"),date,"Asia/Kuala_Lumpur"));
        assertTrue(CancellationPolicy.beforeCheckIn(Instant.parse("2030-01-02T01:00:00Z"),date,"America/New_York"));
    }
}
