package dev.roadmap.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * @DataJpaTest = JPA beans only, Flyway runs, and every test method runs in a transaction that is ROLLED BACK.
 * One shared annotation means one cached Spring context, so one Postgres container for all slice tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@Import(TestcontainersConfiguration.class)
public @interface JpaSliceTest {
}
