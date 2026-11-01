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
public final class CatalogDto {
    private CatalogDto() {}
    public record ServiceInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=2000) String description,
        @NotBlank @Size(max=60) String category, @NotNull @Pattern(regexp="GROUP|PRIVATE") String kind,
        @Min(5) @Max(480) int durationMinutes, @NotNull @Pattern(regexp="BEGINNER|INTERMEDIATE|ADVANCED|ALL_LEVELS") String difficulty,
        @Min(1) @Max(100) int capacity, @NotNull Boolean active) {}
    public record ServiceView(UUID id, String name, String description, String category, String kind,
                              int durationMinutes, String difficulty, int capacity, boolean active) {}
    public record SessionInput(@NotNull UUID serviceId, @NotNull UUID trainerId,
        @NotBlank @Size(max=120) String location, @NotNull Instant startsAt, @Min(1) @Max(100) int capacity,
        @NotNull @Pattern(regexp="DRAFT|PUBLISHED") String state) {}
    public record SessionView(UUID id, UUID serviceId, String name, String description, String category, String kind,
        String difficulty, UUID trainerId, String trainerName, String location, Instant startsAt, Instant endsAt,
        int capacity, int reservedCount, int available, String state, int cancellationMinutes) {}
    public record Trainer(UUID id, String displayName) {}
}
