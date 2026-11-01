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

@Entity @Table(name="payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="order_id", nullable=false)
    private UUID orderId;
    @Column(name="provider", nullable=false, length=50)
    private String provider;
    @Column(name="merchant_id", nullable=false, length=100)
    private String merchantId;
    @Column(name="external_id", nullable=false, length=150)
    private String externalId;
    @Column(name="amount_minor", nullable=false)
    private Long amountMinor;
    @Column(name="currency", nullable=false, length=3)
    private String currency;
    @Column(name="status", nullable=false, length=12)
    private String status;
    @Column(name="disposition", nullable=false, length=12)
    private String disposition;
    @Column(name="simulated", nullable=false)
    private Boolean simulated;
    @Column(name="captured_at", nullable=true)
    private Instant capturedAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_id", insertable=false, updatable=false)
    private MembershipOrder orderReference;
}
