package com.jeyz9.oasisfixture;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Capabilities;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Checkout;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.RefundDispatch;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto;
import com.jeyz9.oasisapi.service.OrderService;
import com.jeyz9.oasisapi.service.PaymentProcessingService;
import com.jeyz9.oasisapi.component.PaymentGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

/** Explicit fixture import outside application component scanning, excluded from production JARs. */
@TestConfiguration(proxyBeanMethods=false)
public class PaymentSimulationConfiguration {
    @Bean @Primary
    public LocalGateway localGateway(Clock clock,ObjectMapper json,Environment env) {
        String url=env.getProperty("spring.datasource.url","");
        boolean test=url.equals("jdbc:postgresql://127.0.0.1:55439/oasis_phase1_test") && "true".equals(System.getenv("OASIS_PG_TEST"));
        boolean demo=url.equals("jdbc:postgresql://127.0.0.1:55439/oasis_phase1_demo") && "true".equals(System.getenv("OASIS_LOCAL_FIXTURE"));
        if(!test && !demo) throw new IllegalStateException("Payment simulator requires an explicit isolated local fixture");
        return new LocalGateway(clock,json);
    }
    @Bean @Primary public LocalNotifications localNotifications(LocalGateway guardedFixture){return new LocalNotifications();}
    public static class LocalNotifications implements com.jeyz9.oasisapi.component.NotificationTransport {
        public final java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();
        public final Set<UUID> delivered=java.util.concurrent.ConcurrentHashMap.newKeySet();
        public Result send(UUID id,String channel,String destination,String title,String body) {
            if(failures.getAndUpdate(n->Math.max(0,n-1))>0)return new Result("RETRY",true,"SIMULATED_TRANSIENT_FAILURE");
            delivered.add(id);return new Result("DELIVERED",true,"SIMULATED_DELIVERY");
        }
    }
    public static final class LocalGateway implements PaymentGateway {
        private final byte[] secret=new byte[32];
        private final Clock clock;private final ObjectMapper json;
        public LocalGateway(Clock clock,ObjectMapper json) {this.clock=clock;this.json=json;new SecureRandom().nextBytes(secret);}
        public Capabilities capabilities() {return new Capabilities("LOCAL_SIMULATOR",true,true,List.of("CARD","PROMPTPAY"));}
        public Checkout checkout(UUID id,long amount,String currency) {return new Checkout(id,amount,currency,true,null,capabilities().methods());}
        public RefundDispatch requestRefund(UUID id,String payment,long amount,String currency) {return new RefundDispatch(id,"sim-refund-"+id,true);}
        public String timestamp() {return Long.toString(clock.instant().getEpochSecond());}
        public String sign(byte[] body,String timestamp) {
            try {
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
                mac.update((timestamp+".").getBytes(StandardCharsets.US_ASCII));return HexFormat.of().formatHex(mac.doFinal(body));
            } catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
        }
        public byte[] encode(VerifiedEvent event) {return json.writeValueAsBytes(event);}
        public VerifiedEvent verify(byte[] body,String signature,String timestamp) {
            long time;
            try {time=Long.parseLong(timestamp);}catch(RuntimeException e){throw fail(400,"INVALID_SIGNATURE");}
            long now=clock.instant().getEpochSecond();
            if(time<now-300 || time>now+300 || signature==null || !signature.matches("[0-9a-f]{64}") ||
                !MessageDigest.isEqual(HexFormat.of().parseHex(sign(body,timestamp)),HexFormat.of().parseHex(signature))) throw fail(400,"INVALID_SIGNATURE");
            final VerifiedEvent event;
            try {event=json.readValue(body,VerifiedEvent.class);}catch(RuntimeException e){throw fail(400,"INVALID_PAYMENT_EVENT");}
            if(event.eventId()==null || !event.eventId().matches("[A-Za-z0-9_-]{1,150}") || event.orderId()==null ||
                event.paymentId()==null || !event.paymentId().matches("[A-Za-z0-9_-]{1,150}") || event.amountMinor()<=0 ||
                event.currency()==null || !event.currency().matches("[A-Z]{3}") || !"local-merchant".equals(event.merchantId()) ||
                event.type()==null || !Set.of("PAYMENT_SUCCEEDED","PAYMENT_FAILED","REFUND_SUCCEEDED","REFUND_FAILED").contains(event.type()) ||
                (event.refundReference()!=null && !event.refundReference().matches("[A-Za-z0-9_-]{1,150}"))) throw fail(400,"INVALID_PAYMENT_EVENT");
            return event;
        }
        public PaymentProcessingDto.EventResult deliver(PaymentProcessingService processing,VerifiedEvent event) {
            byte[] body=encode(event);String timestamp=timestamp();return processing.webhook(body,sign(body,timestamp),timestamp);
        }
    }
    @RestController @RequestMapping("/api/v1/local-payment-simulator")
    public static class SimulatorController {
        public record Confirm(@NotNull @Pattern(regexp="CARD|PROMPTPAY") String method) {}
        public record RefundConfirmation(@NotNull Boolean succeeded) {}
        private final LocalGateway gateway;private final OrderService orders;private final PaymentProcessingService processing;private final JdbcTemplate db;
        public SimulatorController(LocalGateway gateway,OrderService orders,PaymentProcessingService processing,JdbcTemplate db) {this.gateway=gateway;this.orders=orders;this.processing=processing;this.db=db;}
        @PostMapping("/orders/{id}/confirm") @PreAuthorize("hasRole('CUSTOMER')")
        public PaymentProcessingDto.EventResult confirm(@PathVariable UUID id,@Valid @RequestBody Confirm input) {
            var order=orders.own(id);
            if(order.status().equals("PAID")) return new PaymentProcessingDto.EventResult("PROCESSED","ALREADY_PAID");
            orders.checkout(id);
            return gateway.deliver(processing,new PaymentGatewayDto.VerifiedEvent("sim-event-"+UUID.randomUUID(),"local-merchant",id,"sim-pay-"+id,
                "PAYMENT_SUCCEEDED",order.amountMinor(),order.currency(),null,null));
        }
        @PostMapping("/refunds/{id}/confirm") @PreAuthorize("hasRole('ADMIN')")
        public PaymentProcessingDto.EventResult refund(@PathVariable UUID id,@Valid @RequestBody RefundConfirmation input) {
            var rows=db.queryForList("SELECT f.*,p.order_id,p.external_id AS payment_reference,p.currency FROM refunds f JOIN payments p ON f.payment_id=p.id WHERE f.id=? AND p.simulated",id);
            if(rows.isEmpty()) throw fail(404,"REFUND_NOT_FOUND");var r=rows.getFirst();
            return gateway.deliver(processing,new PaymentGatewayDto.VerifiedEvent("sim-event-"+UUID.randomUUID(),"local-merchant",(UUID)r.get("order_id"),
                (String)r.get("payment_reference"),input.succeeded()?"REFUND_SUCCEEDED":"REFUND_FAILED",((Number)r.get("amount_minor")).longValue(),
                (String)r.get("currency"),id,"sim-refund-"+id));
        }
    }
}
