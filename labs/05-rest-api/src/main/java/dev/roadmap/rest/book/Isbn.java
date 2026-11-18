package dev.roadmap.rest.book;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom constraint: a valid ISBN-13 (hyphens allowed) with a correct check digit.
 * Hibernate Validator already ships org.hibernate.validator.constraints.ISBN. This one exists to show the
 * three attributes every custom constraint must declare: message, groups, payload.
 */
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Isbn {

    String message() default "must be a valid ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
