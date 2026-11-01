package com.jeyz9.oasisapi.service.impl;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.service.OrderService;
import com.jeyz9.oasisapi.repository.OrderRepository;
import com.jeyz9.oasisapi.dto.OrderDto.Create;
import com.jeyz9.oasisapi.dto.OrderDto.Receipt;
import com.jeyz9.oasisapi.dto.OrderDto.View;
import com.jeyz9.oasisapi.component.PaymentGateway;
import com.jeyz9.oasisapi.utils.Actor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service @Validated
public class OrderServiceImpl implements OrderService {
    private final OrderRepository repository;
    private final Clock clock;
    private final PaymentGateway gateway;
    public OrderServiceImpl(OrderRepository repository,Clock clock,PaymentGateway gateway) { this.repository=repository;this.clock=clock;this.gateway=gateway; }

    static Instant instant(Map<String,Object> row,String field) { return (Instant)row.get(field); }

    private View view(Map<String,Object> r) {
        String state=(String)r.get("status");
        if(state.equals("PENDING") && !instant(r,"expires_at").isAfter(clock.instant())) state="EXPIRED";
        return new View((UUID)r.get("id"),(UUID)r.get("customer_id"),(String)r.get("customer_name"),(UUID)r.get("plan_id"),(String)r.get("name"),((Number)r.get("amount_minor")).longValue(),
            (String)r.get("currency"),state,(Boolean)r.get("simulated"),instant(r,"created_at"),instant(r,"expires_at"),instant(r,"paid_at"),
            (UUID)r.get("grant_id"),(UUID)r.get("receipt_id"),(UUID)r.get("payment_id"),(String)r.get("refund_status"));
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public View own(UUID id) {
        var rows=repository.findOwnRows(id,Actor.id());
        if(rows.isEmpty()) throw fail(404,"ORDER_NOT_FOUND");return view(rows.getFirst());
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<View> history(int offset,int limit) {
        return repository.findHistoryRows(Actor.id(),limit,offset).stream().map(this::view).toList();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<View> all() { return repository.findAllRows().stream().map(this::view).toList(); }
    @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    public View create(String key,Create input) {
        UUID customer=Actor.id();
        var existing=repository.findCreateMembershipOrdersIdPlanId(customer,key);
        if(!existing.isEmpty()) {
            if(!input.planId().equals(existing.getFirst().get("plan_id"))) throw fail(409,"IDEMPOTENCY_CONFLICT");
            return own((UUID)existing.getFirst().get("id"));
        }
        var plans=repository.findCreateMembershipPlansLocked(input.planId());
        if(plans.isEmpty()) throw fail(404,"PLAN_NOT_FOUND");var p=plans.getFirst();
        if(!(Boolean)p.get("active")) throw fail(422,"PLAN_INACTIVE");
        if(((Number)p.get("price_minor")).longValue()<=0) throw fail(422,"FREE_PLAN_USE_COMPLIMENTARY");
        UUID id=UUID.randomUUID();Instant now=clock.instant();
        int inserted=repository.writeCreateMembershipOrders(id,customer,key,gateway.capabilities().simulated(),now,now.plus(Duration.ofMinutes(30)),input.planId());
        if(inserted==1) repository.insertCreateOrderServices(id,input.planId());
        var row=repository.findCreateMembershipOrdersIdPlanIdRow(customer,key);
        if(!input.planId().equals(row.get("plan_id"))) throw fail(409,"IDEMPOTENCY_CONFLICT");
        return own((UUID)row.get("id"));
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public PaymentGatewayDto.Checkout checkout(UUID id) {
        View order=own(id);
        if(!order.status().equals("PENDING")) throw fail(409,"ORDER_NOT_PAYABLE");
        var capability=gateway.capabilities();
        if(!capability.enabled()) throw fail(503,"PAYMENTS_UNCONFIGURED");
        if(capability.simulated()!=order.simulated()) throw fail(409,"PAYMENT_MODE_MISMATCH");
        // No network operation is held inside a database transaction.
        return gateway.checkout(order.id(),order.amountMinor(),order.currency());
    }
    @PreAuthorize("hasRole('CUSTOMER')") @Transactional
    public View cancel(UUID id) {
        var rows=repository.findCancelMembershipOrdersLocked(id,Actor.id());
        if(rows.isEmpty()) throw fail(404,"ORDER_NOT_FOUND");String status=(String)rows.getFirst().get("status");
        if(status.equals("CANCELLED")) return own(id);
        if(!status.equals("PENDING")) throw fail(409,"ORDER_NOT_CANCELLABLE");
        repository.updateCancelMembershipOrdersStatusCancelled(id);return own(id);
    }
    @PreAuthorize("hasRole('CUSTOMER')")
    public Receipt receipt(UUID id) {
        var rows=repository.findReceiptReceipts(id,Actor.id());
        if(rows.isEmpty()) throw fail(404,"RECEIPT_NOT_FOUND");var r=rows.getFirst();
        return new Receipt(id,(String)r.get("receipt_number"),(UUID)r.get("order_id"),(String)r.get("customer_name"),(String)r.get("plan_name"),
            ((Number)r.get("amount_minor")).longValue(),(String)r.get("currency"),(Boolean)r.get("simulated"),instant(r,"issued_at"),(String)r.get("external_id"));
    }
}
