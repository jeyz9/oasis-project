package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="membership_plans")
@Getter @Setter @NoArgsConstructor
public class MembershipPlan {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
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
    @Column(name="price_minor", nullable=false)
    private Long priceMinor;
    @Column(name="currency", nullable=false, length=3)
    private String currency;
    @Column(name="active", nullable=false)
    private Boolean active;
}
