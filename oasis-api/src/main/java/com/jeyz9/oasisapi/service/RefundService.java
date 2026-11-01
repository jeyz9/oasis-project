package com.jeyz9.oasisapi.service;

import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.RefundDto.Request;
import com.jeyz9.oasisapi.dto.RefundDto.View;

public interface RefundService {
    View request(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String key,@Valid Request input);
    PaymentGatewayDto.RefundDispatch dispatch(UUID id);
    List<View> all();
}
