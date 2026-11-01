package com.jeyz9.oasisapi.component;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Capabilities;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Checkout;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.RefundDispatch;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent;
import java.util.UUID;

/** Provider-specific code must verify identity/signature over raw bytes before returning an event. */
public interface PaymentGateway {
    Capabilities capabilities();
    Checkout checkout(UUID orderId,long amountMinor,String currency);
    /** Use refundId as the provider's stable idempotency identity; do not dispatch inside DB locks. */
    default RefundDispatch requestRefund(UUID refundId,String paymentReference,long amountMinor,String currency) {
        throw com.jeyz9.oasisapi.component.ApiError.fail(503,"REFUND_DISPATCH_UNAVAILABLE");
    }
    VerifiedEvent verify(byte[] rawBody,String signature,String timestamp);
}
