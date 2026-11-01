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
import java.util.UUID;

@Entity @Table(name="notification_preferences")
@Getter @Setter @NoArgsConstructor
public class NotificationPreference {
    @Id
    @Column(name="user_id", nullable=false)
    private UUID userId;
    @Column(name="email_enabled", nullable=false)
    private Boolean emailEnabled;
    @Column(name="line_enabled", nullable=false)
    private Boolean lineEnabled;
    @Column(name="line_destination", nullable=true, length=200)
    private String lineDestination;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id", insertable=false, updatable=false)
    private User userReference;
}
