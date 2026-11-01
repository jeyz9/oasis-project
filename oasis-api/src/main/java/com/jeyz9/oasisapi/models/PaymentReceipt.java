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
@Entity @Table(name="receipts")
@Getter @Setter @NoArgsConstructor
public class PaymentReceipt {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="payment_id", nullable=false)
    private UUID paymentId;
    @Column(name="order_id", nullable=false)
    private UUID orderId;
    @Column(name="receipt_number", nullable=false, length=80)
    private String receiptNumber;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="customer_name", nullable=false, length=100)
    private String customerName;
    @Column(name="plan_name", nullable=false, length=120)
    private String planName;
    @Column(name="amount_minor", nullable=false)
    private Long amountMinor;
    @Column(name="currency", nullable=false, length=3)
    private String currency;
    @Column(name="simulated", nullable=false)
    private Boolean simulated;
    @Column(name="issued_at", nullable=false)
    private Instant issuedAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="payment_id", insertable=false, updatable=false)
    private Payment paymentReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="order_id", insertable=false, updatable=false)
    private MembershipOrder orderReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
}
