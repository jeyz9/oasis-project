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
import java.util.UUID;

@Entity @Table(name="trainer_terms")
@Getter @Setter @NoArgsConstructor
public class TrainerTerm {
    @Id
    @Column(name="trainer_id", nullable=false)
    private UUID trainerId;
    @Column(name="commission_minor", nullable=false)
    private Long commissionMinor;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="trainer_id", insertable=false, updatable=false)
    private User trainerReference;
}
