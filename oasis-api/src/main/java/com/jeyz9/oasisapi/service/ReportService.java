package com.jeyz9.oasisapi.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.jeyz9.oasisapi.dto.ReportDto.Sales;
import com.jeyz9.oasisapi.dto.ReportDto.Trainer;

public interface ReportService {
    Sales sales(LocalDate from,LocalDate to,String grouping,String mode);
    List<Trainer> trainers(LocalDate from,LocalDate to);
    List<Trainer> own(LocalDate from,LocalDate to);
    Map<String,Long> operations();
    boolean healthy();
}
