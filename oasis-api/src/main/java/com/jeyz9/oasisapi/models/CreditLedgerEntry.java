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

@org.hibernate.annotations.Immutable
@Entity @Table(name="credit_ledger")
@Getter @Setter @NoArgsConstructor
public class CreditLedgerEntry {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="grant_id", nullable=false)
    private UUID grantId;
    @Column(name="booking_id", nullable=true)
    private UUID bookingId;
    @Column(name="kind", nullable=false, length=10)
    private String kind;
    @Column(name="delta", nullable=false)
    private Integer delta;
    @Column(name="reverses_id", nullable=true)
    private UUID reversesId;
    @Column(name="reason", nullable=false, length=250)
    private String reason;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="grant_id", insertable=false, updatable=false)
    private MembershipGrant grantReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="booking_id", insertable=false, updatable=false)
    private Booking bookingReference;
}
