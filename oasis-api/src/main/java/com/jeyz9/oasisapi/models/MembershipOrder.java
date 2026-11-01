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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="membership_orders")
@Getter @Setter @NoArgsConstructor
public class MembershipOrder {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="plan_id", nullable=false)
    private UUID planId;
    @Column(name="request_key", nullable=false, length=80)
    private String requestKey;
    @Column(name="name", nullable=false, length=120)
    private String name;
    @Column(name="period", nullable=false, length=10)
    private String period;
    @Column(name="entitlement_mode", nullable=false, length=10)
    private String entitlementMode;
    @Column(name="credits", nullable=true)
    private Integer credits;
    @Column(name="validity_days", nullable=true)
    private Integer validityDays;
    @Column(name="amount_minor", nullable=false)
    private Long amountMinor;
    @Column(name="currency", nullable=false, length=3)
    private String currency;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="plan_snapshot", nullable=false, columnDefinition="jsonb")
    private Map<String,Object> planSnapshot;
    @Column(name="status", nullable=false, length=12)
    private String status;
    @Column(name="simulated", nullable=false)
    private Boolean simulated;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="expires_at", nullable=false)
    private Instant expiresAt;
    @Column(name="paid_at", nullable=true)
    private Instant paidAt;
    @Column(name="grant_id", nullable=true)
    private UUID grantId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="plan_id", insertable=false, updatable=false)
    private MembershipPlan planReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="grant_id", insertable=false, updatable=false)
    private MembershipGrant grantReference;
}
