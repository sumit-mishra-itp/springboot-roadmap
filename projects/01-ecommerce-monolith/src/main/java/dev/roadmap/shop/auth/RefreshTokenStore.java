package dev.roadmap.shop.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Lab 07's refresh-token rotation with reuse detection, moved from a synchronized in-memory map to Postgres, as
 * that lab's ponytail comment proposed. The table survives restarts and is shared by every instance.
 *
 * Atomicity comes from ONE conditional UPDATE ... RETURNING, not from a Java lock: Postgres row locking lets only one
 * of two concurrent refreshes flip used=false to true. (A synchronized block around JDBC would also pin the carrier
 * thread of a virtual thread on Java 21.)
 *
 * Refresh tokens are random strings, NOT JWTs, so they can never pass as access tokens. Only their SHA-256 is stored.
 * Plain SQL through JdbcClient, because the point here is the exact statement. It joins the caller's JPA transaction.
 */
@Component
class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    record Rotation(long userId, String refreshToken) {
    }

    private record Claimed(long userId, UUID familyId) {
    }

    private record Presented(UUID familyId, boolean used) {
    }

    private final JdbcClient jdbc;
    private final AuthProperties props;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenStore(JdbcClient jdbc, AuthProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    String startFamily(long userId) {
        return issue(UUID.randomUUID(), userId);
    }

    /**
     * Must run in a transaction that does NOT roll back on InvalidRefreshTokenException. Otherwise the family
     * revocation below is undone by the very exception that reports it.
     */
    Rotation rotate(String refreshToken) {
        String hash = hash(refreshToken);
        var claimed = jdbc.sql("""
                        update refresh_tokens set used = true
                        where token_hash = ? and not used and expires_at > now()
                        returning user_id, family_id""")
                .param(hash)
                .query((rs, row) -> new Claimed(rs.getLong("user_id"), rs.getObject("family_id", UUID.class)))
                .optional();
        if (claimed.isPresent()) {
            return new Rotation(claimed.get().userId(), issue(claimed.get().familyId(), claimed.get().userId()));
        }
        throw rejection(hash);
    }

    /** Explains why the claim failed. A used token coming back means someone holds a copy: revoke its whole family. */
    private InvalidRefreshTokenException rejection(String hash) {
        var presented = jdbc.sql("select family_id, used from refresh_tokens where token_hash = ?")
                .param(hash)
                .query((rs, row) -> new Presented(rs.getObject("family_id", UUID.class), rs.getBoolean("used")))
                .optional();
        if (presented.isEmpty()) {
            return new InvalidRefreshTokenException("Refresh token is invalid");
        }
        if (!presented.get().used()) {
            return new InvalidRefreshTokenException("Refresh token expired");
        }
        jdbc.sql("update refresh_tokens set used = true where family_id = ?").param(presented.get().familyId()).update();
        log.warn("Refresh token reuse detected, token family {} revoked", presented.get().familyId());
        return new InvalidRefreshTokenException("Refresh token reuse detected, all tokens of this login revoked");
    }

    // ponytail: expired rows are never purged. Add a scheduled "delete where expires_at < now()" when the table grows.
    private String issue(UUID familyId, long userId) {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // The expiry is computed by the database clock, the same clock rotate() compares it with.
        jdbc.sql("""
                        insert into refresh_tokens (token_hash, family_id, user_id, expires_at)
                        values (?, ?, ?, now() + ? * interval '1 second')""")
                .params(hash(token), familyId, userId, props.refreshTokenTtl().toSeconds())
                .update();
        return token;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
        }
    }
}
