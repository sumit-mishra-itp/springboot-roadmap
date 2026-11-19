package dev.roadmap.shop.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Mints short-lived RS256 access tokens. A short TTL is the revocation strategy for self-contained JWTs. */
@Component
class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final AuthProperties props;

    AccessTokenIssuer(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /**
     * "sub" is the user id, not the username: it never changes, and Authentication.getName() then yields the id that
     * orders are owned by. Only the role goes in. Security 7's FACTOR_PASSWORD authority describes HOW the user
     * logged in, not WHAT they may do, so it is not copied.
     */
    String issue(long userId, String username, Role role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString()) // jti: lets you deny-list one token if you ever must
                .claim("preferred_username", username)
                .claim("roles", List.of(role.name()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
