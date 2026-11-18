package dev.roadmap.security.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import dev.roadmap.security.config.AppSecurityProperties;

/** Mints short-lived RS256 access tokens. Short TTL is the revocation strategy for self-contained JWTs. */
@Component
public class AccessTokenIssuer {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder encoder;
    private final AppSecurityProperties props;

    public AccessTokenIssuer(JwtEncoder encoder, AppSecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(String username, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString()) // jti: lets you deny-list one token if you ever must
                .claim("roles", roles(authorities))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Only ROLE_* go into the token. Since Security 7 a password login also carries a FACTOR_PASSWORD
     * authority (MFA support), which describes HOW the user logged in, not WHAT they may do.
     */
    private static List<String> roles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
