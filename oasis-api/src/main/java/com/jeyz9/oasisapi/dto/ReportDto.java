package com.jeyz9.oasisapi.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API contracts; entities never cross HTTP boundaries. */
public final class ReportDto {
    private ReportDto() {}
    public record Sale(LocalDate period,long packages,long grossMinor,long refundMinor,long netMinor) {}
    public record Sales(String mode,String grouping,String currency,List<Sale> periods,long unreconciledMinor) {}
    public record Trainer(UUID trainerId,String trainerName,long classesTaught,long capacity,long attended,long noShows,Double utilization,long commissionMinor,String currency) {}
}
