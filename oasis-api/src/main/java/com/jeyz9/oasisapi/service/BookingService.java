package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.BookingDto.Attendee;
import com.jeyz9.oasisapi.dto.BookingDto.BookInput;
import com.jeyz9.oasisapi.dto.BookingDto.Booking;
import com.jeyz9.oasisapi.dto.BookingDto.Entry;
import com.jeyz9.oasisapi.dto.BookingDto.Grant;
import com.jeyz9.oasisapi.dto.BookingDto.Receipt;

public interface BookingService {
    Receipt book(@Pattern(regexp="[A-Za-z0-9_-]{8,80}") @NotNull String key, @Valid BookInput input);
    List<Booking> history(@Min(0) int offset, @Min(1) @Max(100) int limit);
    Booking own(UUID id);
    Booking cancel(UUID id);
    void cancelSession(UUID id, @NotBlank @Size(max=250) String reason);
    List<Grant> grants(@Min(0) int offset, @Min(1) @Max(100) int limit);
    List<Entry> ledger(@Min(0) int offset, @Min(1) @Max(100) int limit);
    List<Attendee> attendees(UUID sessionId);
}
