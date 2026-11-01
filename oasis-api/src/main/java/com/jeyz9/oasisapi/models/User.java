package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="email", nullable=false, length=254)
    private String email;
    @Column(name="password_hash", nullable=false, length=100)
    private String passwordHash;
    @Column(name="display_name", nullable=false, length=100)
    private String displayName;
    @Column(name="enabled", nullable=false)
    private Boolean enabled;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
}
