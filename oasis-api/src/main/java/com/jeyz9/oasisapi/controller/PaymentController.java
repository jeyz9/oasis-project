package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.component.PaymentGateway;
import com.jeyz9.oasisapi.dto.OrderDto;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto;
import com.jeyz9.oasisapi.dto.RefundDto;
import com.jeyz9.oasisapi.service.OrderService;
import com.jeyz9.oasisapi.service.PaymentProcessingService;
import com.jeyz9.oasisapi.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@RestController @RequestMapping("/api/v1")
public class PaymentController {
    private final OrderService orders;private final PaymentProcessingService processing;private final PaymentGateway gateway;private final RefundService refunds;
    public PaymentController(OrderService orders,PaymentProcessingService processing,PaymentGateway gateway,RefundService refunds) {this.orders=orders;this.processing=processing;this.gateway=gateway;this.refunds=refunds;}
    @GetMapping("/payments/capabilities") public PaymentGatewayDto.Capabilities capabilities() {return gateway.capabilities();}
    @PostMapping("/me/orders") @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody OrderDto.Create input) {return orders.create(key,input);}
    @GetMapping("/me/orders") public List<OrderDto.View> history(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) {return orders.history(offset,limit);}
    @GetMapping("/me/orders/{id}") public OrderDto.View own(@PathVariable UUID id) {return orders.own(id);}
    @PostMapping("/me/orders/{id}/checkout") public PaymentGatewayDto.Checkout checkout(@PathVariable UUID id) {return orders.checkout(id);}
    @PostMapping("/me/orders/{id}/cancel") public OrderDto.View cancel(@PathVariable UUID id) {return orders.cancel(id);}
    @GetMapping("/me/receipts/{id}") public OrderDto.Receipt receipt(@PathVariable UUID id) {return orders.receipt(id);}
    @GetMapping("/admin/orders") public List<OrderDto.View> all() {return orders.all();}
    @GetMapping("/admin/payment-events") public List<PaymentProcessingDto.EventView> events() {return processing.events();}
    @GetMapping("/admin/refunds") public List<RefundDto.View> refunds() {return refunds.all();}
    @PostMapping("/admin/refunds") @ResponseStatus(HttpStatus.CREATED)
    public RefundDto.View refund(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RefundDto.Request input) {return refunds.request(key,input);}
    @PostMapping("/admin/refunds/{id}/dispatch")
    public PaymentGatewayDto.RefundDispatch dispatch(@PathVariable UUID id) {return refunds.dispatch(id);}
    @PostMapping("/payments/webhooks/{provider}")
    public PaymentProcessingDto.EventResult webhook(@PathVariable String provider,@RequestHeader("Payment-Signature") String signature,
        @RequestHeader("Payment-Timestamp") String timestamp,HttpServletRequest request)throws IOException {
        if(!provider.equals(gateway.capabilities().provider())) throw fail(404,"PAYMENT_PROVIDER_NOT_FOUND");
        byte[] body=request.getInputStream().readNBytes(65537);
        if(body.length>65536) throw fail(413,"WEBHOOK_TOO_LARGE");
        return processing.webhook(body,signature,timestamp);
    }
}
