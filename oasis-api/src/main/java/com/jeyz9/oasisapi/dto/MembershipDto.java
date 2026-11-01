package com.jeyz9.oasisapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class MembershipDto {
    private MembershipDto() {}
    public record PlanInput(@NotBlank @Size(max=120) String name,
        @NotNull @Pattern(regexp="MONTH|YEAR|PACKAGE") String period,
        @NotNull @Pattern(regexp="CREDITS|UNLIMITED") String entitlementMode,
        Integer credits,Integer validityDays,@Min(0) @Max(1000000000000L) long priceMinor,
        @NotNull Boolean active,@NotEmpty @Size(max=100) List<@NotNull UUID> serviceIds) {}
    public record Plan(UUID id,String name,String period,String entitlementMode,Integer credits,Integer validityDays,
                       long priceMinor,String currency,boolean active,List<UUID> serviceIds) {}
    public record Complimentary(@NotNull UUID customerId,@NotNull UUID planId,@NotNull Instant startsAt,
                                @NotBlank @Size(max=250) String reason) {}
    public record Issued(UUID grantId,Instant validUntil) {}
    public record Customer(UUID id,String displayName,String email) {}
    public record GrantState(@NotNull @Pattern(regexp="ACTIVE|SUSPENDED|REVOKED") String status,@NotBlank @Size(max=250) String reason) {}
    public record AdminGrant(UUID id,String customerName,String name,String entitlementMode,Integer remainingCredits,
                             Instant validFrom,Instant validUntil,String status,String source) {}
}
