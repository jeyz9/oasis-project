package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class BookingDto {
    private BookingDto() {}
    public record BookInput(@NotNull UUID sessionId, @NotNull UUID grantId) {}
    public record Receipt(UUID bookingId, UUID sessionId, UUID grantId, String statusAtConfirmation) {}
    public record Booking(UUID id, UUID sessionId, UUID grantId, String name, String trainerName, String location,
        Instant startsAt, Instant endsAt, String status, Instant bookedAt, Instant cancelledAt, int cancellationMinutes, String sessionState) {}
    public record Grant(UUID id, String name, String source, Instant validFrom, Instant validUntil, String status, Integer remainingCredits, String entitlementMode, long usedSessions) {}
    public record Entry(UUID id, UUID grantId, UUID bookingId, String kind, int delta, String reason, Instant createdAt) {}
    public record Attendee(UUID bookingId, String displayName, String status) {}
    public record CancelReason(@NotBlank @Size(max=250) String reason) {}
}
