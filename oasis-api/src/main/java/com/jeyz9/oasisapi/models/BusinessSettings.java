package com.jeyz9.oasisapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="business_settings")
@Getter @Setter @NoArgsConstructor
public class BusinessSettings {
    @Id
    @Column(name="id", nullable=false)
    private Integer id;
    @Column(name="cancellation_minutes", nullable=true)
    private Integer cancellationMinutes;
}
