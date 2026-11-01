package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class TrainingDto {
    private TrainingDto() {}
    public record AvailabilityInput(@NotNull Instant startsAt,@NotNull Instant endsAt) {}
    public record Availability(UUID id,Instant startsAt,Instant endsAt) {}
    public record Attendance(@NotNull @Pattern(regexp="ATTENDED|NO_SHOW") String status,@NotNull @Size(max=2000) String outcome) {}
    public record Outcome(UUID bookingId,String displayName,String status,String outcome,Instant recordedAt) {}
    public record ReviewInput(@Min(1) @Max(5) int rating,@NotBlank @Size(max=2000) String body) {}
    public record Review(UUID id,UUID bookingId,int rating,String body,Instant createdAt) {}
}
