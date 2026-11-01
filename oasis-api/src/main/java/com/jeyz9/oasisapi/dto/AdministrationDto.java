package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class AdministrationDto {
    private AdministrationDto() {}
    public record User(UUID id,String email,String displayName,boolean enabled,List<String> roles) {}
    public record UserInput(@NotNull Boolean enabled,@NotEmpty @Size(max=3) List<@Pattern(regexp="CUSTOMER|TRAINER|ADMIN") @NotNull String> roles,@NotBlank @Size(max=250) String reason) {}
    public record RoomInput(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=120) String location,@Min(1) @Max(100) int capacity,@NotNull Boolean active) {}
    public record Room(UUID id,String name,String location,int capacity,boolean active) {}
    public record Policy(@Min(0) @Max(10080) int cancellationMinutes,@NotBlank @Size(max=250) String reason) {}
    public record Commission(@Min(0) @Max(1000000000) long commissionMinor,@NotBlank @Size(max=250) String reason) {}
}
