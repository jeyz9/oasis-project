package com.jeyz9.oasisapi.repository;

/** Maps a JPA projection to a DTO; never exposes a JDBC ResultSet. */
@FunctionalInterface
public interface ProjectionMapper<T> {
    T map(ProjectionRow row, int index);
}
