package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.TrainingDto.Attendance;
import com.jeyz9.oasisapi.dto.TrainingDto.Availability;
import com.jeyz9.oasisapi.dto.TrainingDto.AvailabilityInput;
import com.jeyz9.oasisapi.dto.TrainingDto.Outcome;
import com.jeyz9.oasisapi.dto.TrainingDto.Review;
import com.jeyz9.oasisapi.dto.TrainingDto.ReviewInput;

public interface TrainingService {
    List<Availability> availability(LocalDate from,LocalDate to);
    Availability addAvailability(@Valid AvailabilityInput input);
    void removeAvailability(UUID id);
    Outcome record(UUID booking,@Valid Attendance input);
    List<Outcome> outcomes(UUID session);
    void complete(UUID id);
    Review review(UUID booking,@Valid ReviewInput input);
    List<Review> reviews();
}
