package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.service.RefundService;
import com.jeyz9.oasisapi.repository.RefundRepository;
import com.jeyz9.oasisapi.dto.RefundDto.Request;
import com.jeyz9.oasisapi.dto.RefundDto.View;
import com.jeyz9.oasisapi.component.PaymentGateway;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class RefundServiceImpl implements RefundService {

    private final RefundRepository repository;private final Clock clock;private final PaymentGateway gateway;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    public RefundServiceImpl(RefundRepository repository,Clock clock,PaymentGateway gateway,org.springframework.transaction.PlatformTransactionManager tx) {
        this.repository=repository;this.clock=clock;this.gateway=gateway;transactions=new org.springframework.transaction.support.TransactionTemplate(tx);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public View request(String key,Request input) {
        UUID actor=Actor.id();String reason=input.reason().strip();
        var prior=repository.findRequestRefunds(actor,key);
        if(!prior.isEmpty()) {
            var r=prior.getFirst();if(!r.get("payment_id").equals(input.paymentId()) || !r.get("reason").equals(reason)) throw fail(409,"IDEMPOTENCY_CONFLICT");
            return view(r);
        }
        var ids=repository.findRequestPaymentsOrderIdAccepted(input.paymentId());
        if(ids.isEmpty()) throw fail(404,"PAYMENT_NOT_FOUND");
        var order=repository.findRequestMembershipOrdersLocked(ids.getFirst());
        repository.findRequestPaymentsLocked(input.paymentId());
        var existing=repository.findRequestRefundsRows(input.paymentId());
        if(!existing.isEmpty()) {
            var r=existing.getFirst();
            if(r.get("actor_id").equals(actor) && r.get("request_key").equals(key) && r.get("reason").equals(reason)) return view(r);
            throw fail(409,"REFUND_ALREADY_REQUESTED");
        }
        var grant=repository.findRequestMembershipGrantsLocked(order.get("grant_id"));
        if(!order.get("status").equals("PAID") || grant.get("status").equals("REVOKED")) throw fail(409,"REFUND_NOT_ELIGIBLE");
        if(repository.findRequestBookingsCountCancelled(grant.get("id"))>0
            || (grant.get("entitlement_mode").equals("CREDITS") && !grant.get("remaining_credits").equals(order.get("credits")))) throw fail(422,"MEMBERSHIP_ALREADY_USED");
        UUID id=UUID.randomUUID();
        repository.insertRequestRefundsPending(id,input.paymentId(),actor,key,reason,order.get("amount_minor"),clock.instant());
        repository.updateRequestMembershipGrantsStatusSuspended(grant.get("id"));
        repository.insertRequestAuditEventsRequestFullRefund(UUID.randomUUID(),actor,id,reason);
        return new View(id,input.paymentId(),((Number)order.get("amount_minor")).longValue(),"PENDING",reason);
    }
    private View view(Map<String,Object> r) { return new View((UUID)r.get("id"),(UUID)r.get("payment_id"),((Number)r.get("amount_minor")).longValue(),(String)r.get("state"),(String)r.get("reason")); }
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentGatewayDto.RefundDispatch dispatch(UUID id) {
        if(!gateway.capabilities().enabled()) throw fail(503,"PAYMENTS_UNCONFIGURED");
        var rows=repository.findDispatchRefunds(id);
        if(rows.isEmpty()) throw fail(404,"REFUND_NOT_FOUND");var r=rows.getFirst();
        if(!r.get("state").equals("PENDING")) throw fail(409,"REFUND_NOT_PENDING");
        if(!r.get("provider").equals(gateway.capabilities().provider()) || (Boolean)r.get("simulated")!=gateway.capabilities().simulated()) throw fail(409,"PAYMENT_MODE_MISMATCH");
        // A crash/timeout can safely repeat this call only if the adapter honors refundId idempotency.
        var result=gateway.requestRefund(id,(String)r.get("payment_reference"),((Number)r.get("amount_minor")).longValue(),(String)r.get("currency"));
        if(!id.equals(result.refundId()) || result.simulated()!=gateway.capabilities().simulated() || result.providerRefundReference()==null || result.providerRefundReference().isBlank() || result.providerRefundReference().length()>150)
            throw fail(502,"INVALID_REFUND_DISPATCH");
        transactions.executeWithoutResult(status->{
            var current=repository.findDispatchRefundsLocked(id);
            if(current.get("external_id")!=null && !result.providerRefundReference().equals(current.get("external_id"))) throw fail(409,"REFUND_REFERENCE_CONFLICT");
            if(current.get("external_id")==null) repository.updateDispatchRefundsExternalId(result.providerRefundReference(),id);
        });
        return result;
    }
    @PreAuthorize("hasRole('ADMIN')") public List<View> all() {
        return repository.findAllRefunds().stream().map(this::view).toList();
    }
}
