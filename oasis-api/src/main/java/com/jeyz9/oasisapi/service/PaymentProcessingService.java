package com.jeyz9.oasisapi.service;

import java.util.List;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto.EventResult;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto.EventView;

public interface PaymentProcessingService {
    EventResult webhook(byte[] body,String signature,String timestamp);
    List<EventView> events();
}
