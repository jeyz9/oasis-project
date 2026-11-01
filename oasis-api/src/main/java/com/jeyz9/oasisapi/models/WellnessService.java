package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="services")
@Getter @Setter @NoArgsConstructor
public class WellnessService {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="name", nullable=false, length=120)
    private String name;
    @Column(name="description", nullable=false, length=2000)
    private String description;
    @Column(name="category", nullable=false, length=60)
    private String category;
    @Column(name="kind", nullable=false, length=10)
    private String kind;
    @Column(name="duration_minutes", nullable=false)
    private Integer durationMinutes;
    @Column(name="difficulty", nullable=false, length=20)
    private String difficulty;
    @Column(name="capacity", nullable=false)
    private Integer capacity;
    @Column(name="active", nullable=false)
    private Boolean active;
}
