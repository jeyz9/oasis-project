package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class NotificationDto {
    private NotificationDto() {}
    public record Preferences(boolean emailEnabled,boolean lineEnabled,boolean lineLinked) {}
    public record PreferenceInput(@NotNull Boolean emailEnabled,@NotNull Boolean lineEnabled) {}
    public record Inbox(UUID id,String title,String body,Instant createdAt,Instant readAt) {}
    public record Retry(@NotBlank @Size(max=250) String reason) {}
}
