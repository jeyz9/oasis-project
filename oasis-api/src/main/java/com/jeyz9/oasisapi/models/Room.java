package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="rooms")
@Getter @Setter @NoArgsConstructor
public class Room {
    @Id
    @Column(name="id", nullable=false)
    private UUID id;
    @Column(name="name", nullable=false, length=120)
    private String name;
    @Column(name="location", nullable=false, length=120)
    private String location;
    @Column(name="capacity", nullable=false)
    private Integer capacity;
    @Column(name="active", nullable=false)
    private Boolean active;
    @Column(name="canonical_name", nullable=true, insertable=false, updatable=false)
    private String canonicalName;
}
