package com.jeyz9.oasisapi.repository;

import com.jeyz9.oasisapi.models.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession,UUID> {
    // PESSIMISTIC_WRITE may upgrade to FOR UPDATE: retain PostgreSQL's FK-compatible lock mode.
    @Query(value="SELECT * FROM class_sessions WHERE id=:id FOR NO KEY UPDATE",nativeQuery=true)
    Optional<ClassSession> lockById(UUID id);
}
