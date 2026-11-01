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

@Entity @Table(name="class_sessions")
@Getter @Setter @NoArgsConstructor
public class ClassSession {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="service_id", nullable=false)
    private UUID serviceId;
    @Column(name="trainer_id", nullable=false)
    private UUID trainerId;
    @Column(name="location", nullable=false, length=120)
    private String location;
    @Column(name="starts_at", nullable=false)
    private Instant startsAt;
    @Column(name="ends_at", nullable=false)
    private Instant endsAt;
    @Column(name="capacity", nullable=false)
    private Integer capacity;
    @Column(name="reserved_count", nullable=false)
    private Integer reservedCount;
    @Column(name="state", nullable=false, length=12)
    private String state;
    @Column(name="cancellation_minutes", nullable=false)
    private Integer cancellationMinutes;
    @Column(name="room_id", nullable=false)
    private UUID roomId;
    @Column(name="commission_minor", nullable=false)
    private Long commissionMinor;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="service_id", insertable=false, updatable=false)
    private WellnessService serviceReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="trainer_id", insertable=false, updatable=false)
    private User trainerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="room_id", insertable=false, updatable=false)
    private Room roomReference;
}
