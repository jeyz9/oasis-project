package com.jeyz9.oasisapi.dto;

import java.time.Instant;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class PaymentProcessingDto {
    private PaymentProcessingDto() {}
    public record EventResult(String state,String code) {}
    public record EventView(UUID id,UUID orderId,String provider,String eventType,String state,String code,Instant receivedAt) {}
}
