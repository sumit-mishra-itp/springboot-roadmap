package dev.roadmap.security.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.roadmap.security.config.AppSecurityProperties;

/**
 * Opaque refresh tokens with rotation and reuse detection, the model Auth0 documents.
 * Each login starts a token FAMILY. Every refresh marks the presented token as used and issues the next one
 * in the same family. If a used token comes back, someone holds a copy (a thief, or the user after the thief
 * already rotated), so the whole family is revoked and both parties must log in again.
 *
 * Refresh tokens are random strings, NOT JWTs: if they were JWTs signed with the same key, the API would
 * happily accept them as access tokens. Only a SHA-256 hash is stored, so a leaked store yields no usable tokens.
 */
@Component
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    private record Entry(String familyId, String username, Instant expiresAt, boolean used) {
    }

    public record Rotation(String username, String refreshToken) {
    }

    // ponytail: in-memory and never purged, so one JVM only and tokens vanish on restart. Production stores the
    // hash in a DB table or Redis with a TTL, and rotates with an atomic
    // "UPDATE ... SET used = true WHERE hash = ? AND used = false" so two instances can't both accept one token.
    private final Map<String, Entry> byHash = new ConcurrentHashMap<>();
    private final Set<String> revokedFamilies = ConcurrentHashMap.newKeySet();
    private final SecureRandom random = new SecureRandom();
    private final AppSecurityProperties props;

    public RefreshTokenStore(AppSecurityProperties props) {
        this.props = props;
    }

    public String startFamily(String username) {
        return issue(UUID.randomUUID().toString(), username);
    }

    /** synchronized makes check-and-mark atomic: two concurrent refreshes with one token can't both win. */
    public synchronized Rotation rotate(String refreshToken) {
        String hash = hash(refreshToken);
        Entry entry = byHash.get(hash);
        if (entry == null || revokedFamilies.contains(entry.familyId())) {
            throw new InvalidRefreshTokenException("Refresh token is invalid or revoked");
        }
        if (entry.used()) {
            revokedFamilies.add(entry.familyId());
            log.warn("Refresh token reuse detected for user {}, token family {} revoked", entry.username(),
                    entry.familyId());
            throw new InvalidRefreshTokenException("Refresh token reuse detected, all tokens of this login revoked");
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        byHash.put(hash, new Entry(entry.familyId(), entry.username(), entry.expiresAt(), true));
        return new Rotation(entry.username(), issue(entry.familyId(), entry.username()));
    }

    private String issue(String familyId, String username) {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byHash.put(hash(token), new Entry(familyId, username, Instant.now().plus(props.refreshTokenTtl()), false));
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
