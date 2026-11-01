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

@Entity @Table(name="notification_jobs")
@Getter @Setter @NoArgsConstructor
public class NotificationJob {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="owner_id", nullable=false)
    private UUID ownerId;
    @Column(name="booking_id", nullable=true)
    private UUID bookingId;
    @Column(name="kind", nullable=false, length=30)
    private String kind;
    @Column(name="channel", nullable=false, length=10)
    private String channel;
    @Column(name="dedupe_key", nullable=false, length=150)
    private String dedupeKey;
    @Column(name="title", nullable=false, length=160)
    private String title;
    @Column(name="body", nullable=false, length=1000)
    private String body;
    @Column(name="state", nullable=false, length=12)
    private String state;
    @Column(name="attempts", nullable=false)
    private Integer attempts;
    @Column(name="next_attempt_at", nullable=false)
    private Instant nextAttemptAt;
    @Column(name="lease_until", nullable=true)
    private Instant leaseUntil;
    @Column(name="lease_token", nullable=true)
    private UUID leaseToken;
    @Column(name="last_code", nullable=true, length=80)
    private String lastCode;
    @Column(name="simulated", nullable=false)
    private Boolean simulated;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="delivered_at", nullable=true)
    private Instant deliveredAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="owner_id", insertable=false, updatable=false)
    private User ownerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="booking_id", insertable=false, updatable=false)
    private Booking bookingReference;
}
