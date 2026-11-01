package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.NotificationDto.Inbox;
import com.jeyz9.oasisapi.dto.NotificationDto.PreferenceInput;
import com.jeyz9.oasisapi.dto.NotificationDto.Preferences;
import com.jeyz9.oasisapi.dto.NotificationDto.Retry;

public interface NotificationService {
    void confirmed(UUID booking);
    void cancelled(UUID booking);
    int drain();
    List<Inbox> inbox(@Min(0) int offset,@Min(1) @Max(100) int limit);
    void read(UUID id);
    Preferences preferences();
    Preferences preferences(@Valid PreferenceInput input);
    List<Map<String,Object>> jobs();
    void retry(UUID id,@Valid Retry input);
    Map<String,Integer> manualRun();
}
