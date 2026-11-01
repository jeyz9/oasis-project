package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class RefundDto {
    private RefundDto() {}
    public record Request(@NotNull UUID paymentId,@NotBlank @Size(max=250) String reason) {}
    public record View(UUID id,UUID paymentId,long amountMinor,String state,String reason) {}
}
