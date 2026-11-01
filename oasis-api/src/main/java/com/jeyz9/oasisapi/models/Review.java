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

@Entity @Table(name="reviews")
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="booking_id", nullable=false)
    private UUID bookingId;
    @Column(name="customer_id", nullable=false)
    private UUID customerId;
    @Column(name="rating", nullable=false)
    private Integer rating;
    @Column(name="body", nullable=false, length=2000)
    private String body;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="booking_id", insertable=false, updatable=false)
    private Booking bookingReference;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="customer_id", insertable=false, updatable=false)
    private User customerReference;
}
