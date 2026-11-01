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

@Entity @Table(name="membership_grants")
@Getter @Setter @NoArgsConstructor
public class MembershipGrant {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="name", nullable=false, length=120)
    private String name;
    @Column(name="source", nullable=false, length=30)
    private String source;
    @Column(name="valid_from", nullable=false)
    private Instant validFrom;
    @Column(name="valid_until", nullable=false)
    private Instant validUntil;
    @Column(name="status", nullable=false, length=12)
    private String status;
    @Column(name="remaining_credits", nullable=true)
    private Integer remainingCredits;
    @Column(name="entitlement_mode", nullable=false, length=10)
    private String entitlementMode;
    @Column(name="plan_id", nullable=true)
    private UUID planId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="plan_snapshot", nullable=true, columnDefinition="jsonb")
    private Map<String,Object> planSnapshot;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="plan_id", insertable=false, updatable=false)
    private MembershipPlan planReference;
}
