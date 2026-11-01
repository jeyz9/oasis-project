package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="trainer_availability")
@Getter @Setter @NoArgsConstructor
public class TrainerAvailability {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="trainer_id", nullable=false)
    private UUID trainerId;
    @Column(name="starts_at", nullable=false)
    private Instant startsAt;
    @Column(name="ends_at", nullable=false)
    private Instant endsAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="trainer_id", insertable=false, updatable=false)
    private User trainerReference;
}
