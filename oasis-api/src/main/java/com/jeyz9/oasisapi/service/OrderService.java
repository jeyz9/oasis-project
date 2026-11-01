package com.jeyz9.oasisapi.service;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.OrderDto.Create;
import com.jeyz9.oasisapi.dto.OrderDto.Receipt;
import com.jeyz9.oasisapi.dto.OrderDto.View;

public interface OrderService {
    View own(UUID id);
    List<View> history(@Min(0) int offset,@Min(1) @Max(100) int limit);
    List<View> all();
    View create(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String key,@Valid Create input);
    PaymentGatewayDto.Checkout checkout(UUID id);
    View cancel(UUID id);
    Receipt receipt(UUID id);
}
