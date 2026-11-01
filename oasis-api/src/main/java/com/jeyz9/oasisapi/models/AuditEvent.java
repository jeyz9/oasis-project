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
@Entity @Table(name="audit_events")
@Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="actor_id", nullable=false)
    private UUID actorId;
    @Column(name="action", nullable=false, length=60)
    private String action;
    @Column(name="resource_id", nullable=false)
    private UUID resourceId;
    @Column(name="reason", nullable=false, length=250)
    private String reason;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="actor_id", insertable=false, updatable=false)
    private User actorReference;
}
