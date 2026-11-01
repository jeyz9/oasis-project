package com.jeyz9.oasisapi.dto;

import java.util.List;
import java.util.UUID;

public final class PaymentGatewayDto {
    private PaymentGatewayDto() {}
    public record Capabilities(String provider,boolean enabled,boolean simulated,List<String> methods) {}
    public record Checkout(UUID orderId,long amountMinor,String currency,boolean simulated,String checkoutUrl,List<String> methods) {}
    public record VerifiedEvent(String eventId,String merchantId,UUID orderId,String paymentId,String type,
                         long amountMinor,String currency,UUID refundId,String refundReference) {}
    public record RefundDispatch(UUID refundId,String providerRefundReference,boolean simulated) {}
}
