package dev.roadmap.aop.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a business action that must leave a compliance trail entry. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Business action name written to the trail, e.g. "FUNDS_TRANSFER". */
    String action();
}
