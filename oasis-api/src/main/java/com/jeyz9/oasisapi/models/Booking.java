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

@Entity @Table(name="bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="session_id", nullable=false)
    private UUID sessionId;
    @Column(name="grant_id", nullable=false)
    private UUID grantId;
    @Column(name="status", nullable=false, length=12)
    private String status;
    @Column(name="booked_at", nullable=false)
    private Instant bookedAt;
    @Column(name="cancelled_at", nullable=true)
    private Instant cancelledAt;
    @Column(name="cost", nullable=false)
    private Integer cost;
    @Column(name="starts_at", nullable=false)
    private Instant startsAt;
    @Column(name="ends_at", nullable=false)
    private Instant endsAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="session_id", insertable=false, updatable=false)
    private ClassSession sessionReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="grant_id", insertable=false, updatable=false)
    private MembershipGrant grantReference;
}
