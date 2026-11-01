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

@Entity @Table(name="booking_requests")
@Getter @Setter @NoArgsConstructor
@IdClass(BookingRequest.Key.class)
public class BookingRequest {
    @Id
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Id
    @Column(name="request_key", nullable=false, length=80)
    private String requestKey;
    @Column(name="session_id", nullable=false)
    private UUID sessionId;
    @Column(name="grant_id", nullable=false)
    private UUID grantId;
    @Column(name="booking_id", nullable=true)
    private UUID bookingId;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="session_id", insertable=false, updatable=false)
    private ClassSession sessionReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="grant_id", insertable=false, updatable=false)
    private MembershipGrant grantReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="booking_id", insertable=false, updatable=false)
    private Booking bookingReference;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID customerId;
        private String requestKey;
    }
}
