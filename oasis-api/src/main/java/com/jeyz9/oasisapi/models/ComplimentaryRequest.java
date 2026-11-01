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
import java.time.Instant;
import java.util.UUID;
import java.io.Serializable;

@Entity @Table(name="complimentary_requests")
@Getter @Setter @NoArgsConstructor
@IdClass(ComplimentaryRequest.Key.class)
public class ComplimentaryRequest {
    @Id
    @Column(name="actor_id", nullable=false)
    private UUID actorId;
    @Id
    @Column(name="request_key", nullable=false, length=80)
    private String requestKey;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="plan_id", nullable=false)
    private UUID planId;
    @Column(name="starts_at", nullable=false)
    private Instant startsAt;
    @Column(name="reason", nullable=false, length=250)
    private String reason;
    @Column(name="grant_id", nullable=true)
    private UUID grantId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="actor_id", insertable=false, updatable=false)
    private User actorReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="plan_id", insertable=false, updatable=false)
    private MembershipPlan planReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="grant_id", insertable=false, updatable=false)
    private MembershipGrant grantReference;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID actorId;
        private String requestKey;
    }
}
