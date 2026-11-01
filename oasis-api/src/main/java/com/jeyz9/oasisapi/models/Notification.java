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

@Entity @Table(name="notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="owner_id", nullable=false)
    private UUID ownerId;
    @Column(name="title", nullable=false, length=160)
    private String title;
    @Column(name="body", nullable=false, length=1000)
    private String body;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="read_at", nullable=true)
    private Instant readAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="owner_id", insertable=false, updatable=false)
    private User ownerReference;
}
