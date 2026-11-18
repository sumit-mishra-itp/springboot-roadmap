package dev.roadmap.aop.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method whose execution time is logged. RUNTIME retention is required: Spring AOP matches via reflection. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Loggable {

    /** Calls slower than this are logged at WARN instead of DEBUG. */
    long slowThresholdMs() default 500;
}
