package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.WellnessService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface WellnessServiceRepository extends JpaRepository<WellnessService,UUID> {
    List<WellnessService> findByActiveTrueOrderByNameAsc(Pageable page);
    List<WellnessService> findAllByOrderByNameAsc(Pageable page);
}
