package com.jeyz9.oasisapi.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Typed access to native JPA projections. All instants are normalized to UTC. */
public record ProjectionRow(Map<String,Object> values) {
    public Object getObject(String column) { return values.get(column); }
    public <T> T getObject(String column,Class<T> type) { return type.cast(values.get(column)); }
    public String getString(String column) { return (String)values.get(column); }
    public int getInt(String column) { return ((Number)values.get(column)).intValue(); }
    public long getLong(String column) { return ((Number)values.get(column)).longValue(); }
    public boolean getBoolean(String column) { return (Boolean)values.get(column); }
    public Instant getInstant(String column) { return (Instant)values.get(column); }
    public LocalDate getDate(String column) { return (LocalDate)values.get(column); }
}
