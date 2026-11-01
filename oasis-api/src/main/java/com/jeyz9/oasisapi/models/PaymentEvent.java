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

@Entity @Table(name="payment_events")
@Getter @Setter @NoArgsConstructor
public class PaymentEvent {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="provider", nullable=false, length=50)
    private String provider;
    @Column(name="merchant_id", nullable=false, length=100)
    private String merchantId;
    @Column(name="external_event_id", nullable=false, length=150)
    private String externalEventId;
    @Column(name="payload_hash", nullable=false, length=64)
    private String payloadHash;
    @Column(name="order_id", nullable=false)
    private UUID orderId;
    @Column(name="event_type", nullable=false, length=30)
    private String eventType;
    @Column(name="state", nullable=false, length=12)
    private String state;
    @Column(name="code", nullable=false, length=80)
    private String code;
    @Column(name="received_at", nullable=false)
    private Instant receivedAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_id", insertable=false, updatable=false)
    private MembershipOrder orderReference;
}
