package com.jeyz9.oasisapi.utils;

import java.time.Instant;
import java.util.Map;

public final class TimeValues {
    private TimeValues() {}
    public static Instant instant(Map<String,Object> row,String field) { return (Instant)row.get(field); }
}
