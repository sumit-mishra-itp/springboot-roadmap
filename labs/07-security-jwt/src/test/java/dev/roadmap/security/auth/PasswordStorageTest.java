package dev.roadmap.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Plain JUnit: what DelegatingPasswordEncoder actually stores and how it handles old hashes. */
class PasswordStorageTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void encode_prefixesTheAlgorithmId_andSaltsEveryHash() {
        String first = encoder.encode("s3cret");
        String second = encoder.encode("s3cret");

        assertThat(first).startsWith("{bcrypt}$2a$10$"); // id, bcrypt version, cost factor 10
        assertThat(first).isNotEqualTo(second);           // random salt: equal passwords, different hashes
        assertThat(encoder.matches("s3cret", first)).isTrue();
        assertThat(encoder.matches("wrong", first)).isFalse();
    }

    @Test
    void legacyHash_stillMatches_butIsFlaggedForUpgrade() {
        String legacy = "{noop}s3cret";

        assertThat(encoder.matches("s3cret", legacy)).isTrue();
        assertThat(encoder.upgradeEncoding(legacy)).isTrue();
        assertThat(encoder.upgradeEncoding(encoder.encode("s3cret"))).isFalse();
    }
}
