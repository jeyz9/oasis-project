package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;
import java.io.Serializable;

@Entity @Table(name="plan_services")
@Getter @Setter @NoArgsConstructor
@IdClass(PlanService.Key.class)
public class PlanService {
    @Id
    @Column(name="plan_id", nullable=false)
    private UUID planId;
    @Id
    @Column(name="service_id", nullable=false)
    private UUID serviceId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="plan_id", insertable=false, updatable=false)
    private MembershipPlan planReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="service_id", insertable=false, updatable=false)
    private WellnessService serviceReference;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID planId;
        private UUID serviceId;
    }
}
