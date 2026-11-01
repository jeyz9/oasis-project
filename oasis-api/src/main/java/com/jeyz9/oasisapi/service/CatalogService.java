package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.CatalogDto.ServiceInput;
import com.jeyz9.oasisapi.dto.CatalogDto.ServiceView;
import com.jeyz9.oasisapi.dto.CatalogDto.SessionInput;
import com.jeyz9.oasisapi.dto.CatalogDto.SessionView;
import com.jeyz9.oasisapi.dto.CatalogDto.Trainer;

public interface CatalogService {
    List<ServiceView> services();
    List<ServiceView> adminServices();
    List<Trainer> trainers();
    ServiceView saveService(UUID id, @Valid ServiceInput input);
    List<SessionView> schedule(LocalDate from, LocalDate to);
    SessionView session(UUID id);
    List<SessionView> adminSchedule(LocalDate from, LocalDate to);
    List<SessionView> trainerSchedule(LocalDate from, LocalDate to);
    SessionView saveSession(UUID id, @Valid SessionInput input);
}
