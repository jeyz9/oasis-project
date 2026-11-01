package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class OrderDto {
    private OrderDto() {}
    public record Create(@NotNull UUID planId) {}
    public record View(UUID id,UUID customerId,String customerName,UUID planId,String name,long amountMinor,String currency,String status,boolean simulated,
                       Instant createdAt,Instant expiresAt,Instant paidAt,UUID grantId,UUID receiptId,UUID paymentId,String refundStatus) {}
    public record Receipt(UUID id,String receiptNumber,UUID orderId,String customerName,String planName,long amountMinor,
                          String currency,boolean simulated,Instant issuedAt,String paymentReference) {}
}
