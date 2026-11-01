package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.service.PaymentProcessingService;
import com.jeyz9.oasisapi.repository.PaymentProcessingRepository;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto.EventResult;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto.EventView;
import com.jeyz9.oasisapi.component.PaymentGateway;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service
public class PaymentProcessingServiceImpl implements PaymentProcessingService {

    private final PaymentProcessingRepository repository;
    private final Clock clock;
    private final PaymentGateway gateway;
    private final TransactionTemplate transactions;
    public PaymentProcessingServiceImpl(PaymentProcessingRepository repository,Clock clock,PaymentGateway gateway,PlatformTransactionManager tx) {
        this.repository=repository;this.clock=clock;this.gateway=gateway;transactions=new TransactionTemplate(tx);
    }
    public EventResult webhook(byte[] body,String signature,String timestamp) {
        if(!gateway.capabilities().enabled()) throw fail(503,"PAYMENTS_UNCONFIGURED");
        if(body.length>65536) throw fail(413,"WEBHOOK_TOO_LARGE");
        // Verification is provider-specific. No browser or admin endpoint supplies a trusted event.
        var event=gateway.verify(body,signature,timestamp);
        final String hash;
        try { hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        return transactions.execute(status->process(event,hash));
    }
    private EventResult process(PaymentGatewayDto.VerifiedEvent e,String hash) {
        String provider=gateway.capabilities().provider();
        var orders=repository.findProcessMembershipOrdersId(e.orderId());
        if(orders.isEmpty()) throw fail(404,"ORDER_NOT_FOUND");
        int inserted=repository.writeProcessPaymentEvents(UUID.randomUUID(),provider,e.merchantId(),e.eventId(),hash,e.orderId(),e.type(),clock.instant());
        var stored=repository.findProcessPaymentEventsLocked(provider,e.merchantId(),e.eventId());
        if(!hash.equals(stored.get("payload_hash"))) throw fail(409,"EVENT_ID_CONFLICT");
        if(inserted==0) return new EventResult((String)stored.get("state"),(String)stored.get("code"));
        var order=repository.findProcessMembershipOrdersLocked(e.orderId());
        EventResult result;
        if((Boolean)order.get("simulated")!=gateway.capabilities().simulated()) result=new EventResult("REJECTED","PAYMENT_MODE_MISMATCH");
        else if(((Number)order.get("amount_minor")).longValue()!=e.amountMinor() || !order.get("currency").equals(e.currency())) result=new EventResult("REJECTED","AMOUNT_OR_CURRENCY_MISMATCH");
        else if(e.type().startsWith("REFUND_")) result=refund(e,order,provider);
        else if(e.type().equals("PAYMENT_SUCCEEDED") || e.type().equals("PAYMENT_FAILED")) result=payment(e,order,provider);
        else result=new EventResult("REJECTED","UNSUPPORTED_EVENT");
        repository.updateProcessPaymentEventsStateCode(result.state(),result.code(),stored.get("id"));
        return result;
    }
    private EventResult payment(PaymentGatewayDto.VerifiedEvent e,Map<String,Object> order,String provider) {
        var existing=repository.findPaymentPaymentsLocked(provider,e.merchantId(),e.paymentId());
        if(!existing.isEmpty() && (!existing.getFirst().get("order_id").equals(e.orderId()) || ((Number)existing.getFirst().get("amount_minor")).longValue()!=e.amountMinor() || !existing.getFirst().get("currency").equals(e.currency())))
            return new EventResult("REJECTED","PAYMENT_REFERENCE_CONFLICT");
        UUID payment=UUID.randomUUID();
        if(existing.isEmpty()) {
            repository.writePaymentPayments(payment,e.orderId(),provider,e.merchantId(),e.paymentId(),e.amountMinor(),e.currency(),order.get("simulated"));
            var row=repository.findPaymentPaymentsLockedRow(provider,e.merchantId(),e.paymentId());
            if(!row.get("order_id").equals(e.orderId())) return new EventResult("REJECTED","PAYMENT_REFERENCE_CONFLICT");
            existing=List.of(row);
        }
        var p=existing.getFirst();payment=(UUID)p.get("id");
        if(p.get("status").equals("SUCCEEDED")) return new EventResult("PROCESSED","IGNORED_TERMINAL_PAYMENT");
        if(e.type().equals("PAYMENT_FAILED")) return new EventResult("PROCESSED","PAYMENT_FAILED");
        Instant now=clock.instant();
        String state=(String)order.get("status");
        boolean eligible=state.equals("PENDING") && com.jeyz9.oasisapi.utils.TimeValues.instant(order,"expires_at").isAfter(now)
            && repository.findPaymentUsersCountCustomer(order.get("customer_id"))==1;
        if(!eligible) {
            repository.updatePaymentPaymentsStatusDispositionCapturedAtSucceededReview(now,payment);
            if(!state.equals("PAID")) repository.updatePaymentMembershipOrdersStatusReview(e.orderId());
            return new EventResult("REVIEW",state.equals("PAID")?"EXCESS_CAPTURE":"LATE_OR_INELIGIBLE_CAPTURE");
        }
        UUID grant=fulfill(order,now);
        repository.updatePaymentPaymentsStatusDispositionCapturedAtSucceededAccepted(now,payment);
        repository.updatePaymentMembershipOrdersStatusPaidAtGrantIdPaid(now,grant,e.orderId());
        UUID receipt=UUID.randomUUID();String prefix=(Boolean)order.get("simulated")?"SIM-":"REC-";
        repository.writePaymentReceipts(receipt,payment,e.orderId(),prefix+receipt,order.get("name"),e.amountMinor(),e.currency(),order.get("simulated"),now,order.get("customer_id"));
        return new EventResult("PROCESSED","PAYMENT_FULFILLED");
    }
    private UUID fulfill(Map<String,Object> order,Instant now) {
        var local=now.atZone(ZoneId.of("Asia/Bangkok"));
        Instant until=switch((String)order.get("period")) {
            case "MONTH" -> local.plusMonths(1).toInstant();case "YEAR" -> local.plusYears(1).toInstant();
            default -> local.plusDays(((Number)order.get("validity_days")).longValue()).toInstant();
        };
        UUID grant=UUID.randomUUID();
        repository.writeFulfillMembershipGrants(grant,(Boolean)order.get("simulated")?"SIMULATED_PAYMENT":"VERIFIED_PAYMENT",now,until,order.get("id"));
        repository.insertFulfillGrantServices(grant,order.get("id"));
        if(order.get("entitlement_mode").equals("CREDITS")) repository.insertFulfillCreditLedgerGrant(UUID.randomUUID(),grant,order.get("credits"),(Boolean)order.get("simulated")?"Simulated payment fulfillment":"Verified payment fulfillment",now);
        return grant;
    }
    private EventResult refund(PaymentGatewayDto.VerifiedEvent e,Map<String,Object> order,String provider) {
        if(e.refundId()==null || e.refundReference()==null || e.refundReference().isBlank()) return new EventResult("REJECTED","REFUND_REFERENCE_REQUIRED");
        var rows=repository.findRefundRefunds(e.refundId(),e.orderId(),provider,e.merchantId());
        if(rows.isEmpty()) return new EventResult("REJECTED","REFUND_NOT_FOUND");var r=rows.getFirst();
        if(!e.paymentId().equals(r.get("payment_reference")) || ((Number)r.get("amount_minor")).longValue()!=e.amountMinor()) return new EventResult("REJECTED","REFUND_AMOUNT_MISMATCH");
        if(r.get("external_id")!=null && !e.refundReference().equals(r.get("external_id"))) return new EventResult("REJECTED","REFUND_REFERENCE_CONFLICT");
        if(r.get("state").equals("SUCCEEDED")) return new EventResult("PROCESSED","IGNORED_TERMINAL_REFUND");
        if(!e.type().equals("REFUND_SUCCEEDED") && !e.type().equals("REFUND_FAILED")) return new EventResult("REJECTED","UNSUPPORTED_EVENT");
        repository.findRefundMembershipGrantsLocked(order.get("grant_id"));
        boolean success=e.type().equals("REFUND_SUCCEEDED");
        repository.updateRefundRefundsStateExternalIdConfirmedAt(success?"SUCCEEDED":"FAILED",e.refundReference(),clock.instant(),e.refundId());
        // Failure never silently reactivates access. An admin must decide whether it is safe to resume.
        repository.updateRefundMembershipGrantsStatusRevoked(success?"REVOKED":"SUSPENDED",order.get("grant_id"));
        return new EventResult("PROCESSED",success?"REFUND_CONFIRMED":"REFUND_FAILED_ACCESS_SUSPENDED");
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<EventView> events() {
        return repository.findEventsPaymentEvents((r,n)->new EventView(r.getObject("id",UUID.class),r.getObject("order_id",UUID.class),r.getString("provider"),r.getString("event_type"),r.getString("state"),r.getString("code"),r.getInstant("received_at")));
    }
}
