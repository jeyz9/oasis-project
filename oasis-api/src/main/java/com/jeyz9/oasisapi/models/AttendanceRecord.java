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

@Entity @Table(name="attendance_records")
@Getter @Setter @NoArgsConstructor
public class AttendanceRecord {
    @Id
    @Column(name="booking_id", nullable=false)
    private UUID bookingId;
    @Column(name="recorded_by", nullable=false)
    private UUID recordedBy;
    @Column(name="outcome", nullable=false, length=2000)
    private String outcome;
    @Column(name="recorded_at", nullable=false)
    private Instant recordedAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="booking_id", insertable=false, updatable=false)
    private Booking bookingReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="recorded_by", insertable=false, updatable=false)
    private User recordedByReference;
}
