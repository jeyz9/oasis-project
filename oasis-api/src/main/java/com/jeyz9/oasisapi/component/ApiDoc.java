package com.jeyz9.oasisapi.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Human-readable OpenAPI metadata for an API operation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDoc {
    String summary();
    String description();
    String tag();
    String access();
    boolean csrf() default false;
}
