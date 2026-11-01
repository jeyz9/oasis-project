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

@Entity @Table(name="user_roles")
@Getter @Setter @NoArgsConstructor
@IdClass(UserRole.Key.class)
public class UserRole {
    @Id
    @Column(name="user_id", nullable=false)
    private UUID userId;
    @Id
    @Column(name="role", nullable=false, length=10)
    private String role;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id", insertable=false, updatable=false)
    private User userReference;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID userId;
        private String role;
    }
}
