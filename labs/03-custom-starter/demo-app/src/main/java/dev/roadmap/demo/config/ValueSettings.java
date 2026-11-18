package dev.roadmap.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The same kind of data read with @Value, to contrast with {@link AppProperties}.
 * A record works as a component: its canonical constructor is the injection point.
 */
@Component
public record ValueSettings(
        @Value("${app.support.on-call-email}") String canonicalKey,          // canonical (kebab) form: always resolves
        @Value("${app.support.onCallEmail:not-found}") String camelCaseKey,  // non-canonical form: NOT relaxed against a kebab key
        @Value("${app.region:eu-west-1}") String region,                     // ${key:default} placeholder
        @Value("#{'${app.owner}'.toUpperCase()}") String ownerUpperCase) {   // #{...} is SpEL, evaluated after ${...} resolves
}
