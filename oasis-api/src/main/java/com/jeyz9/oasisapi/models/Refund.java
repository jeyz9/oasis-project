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

@Entity @Table(name="refunds")
@Getter @Setter @NoArgsConstructor
public class Refund {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="payment_id", nullable=false)
    private UUID paymentId;
    @Column(name="actor_id", nullable=false)
    private UUID actorId;
    @Column(name="request_key", nullable=false, length=80)
    private String requestKey;
    @Column(name="reason", nullable=false, length=250)
    private String reason;
    @Column(name="amount_minor", nullable=false)
    private Long amountMinor;
    @Column(name="state", nullable=false, length=12)
    private String state;
    @Column(name="external_id", nullable=true, length=150)
    private String externalId;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="confirmed_at", nullable=true)
    private Instant confirmedAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="payment_id", insertable=false, updatable=false)
    private Payment paymentReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="actor_id", insertable=false, updatable=false)
    private User actorReference;
}
