package dev.roadmap.security.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Token plumbing, all of it Spring Security's own classes. There is no hand-written "JwtFilter":
 * oauth2ResourceServer() installs BearerTokenAuthenticationFilter, which calls this JwtDecoder.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppSecurityProperties.class)
public class JwtConfig {

    // ponytail: the key pair is generated at every start, so tokens die on restart and can't be shared by
    // several instances. In production, load the private key from Secrets Manager or KMS and publish the
    // public half as a JWK set so other services can verify tokens.
    @Bean
    KeyPair jwtSigningKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** Security 7 builder. RS256: only the private-key holder can sign, and anyone with the public key can verify. */
    @Bean
    JwtEncoder jwtEncoder(KeyPair keyPair) {
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .build();
    }

    /**
     * Signature check plus claim validation. The 7.x defaults (createDefaultWithValidators) add the exp/nbf
     * timestamp check (60s clock skew) and the JWT typ-header check. We add issuer and audience on top, so a
     * correctly signed token minted for ANOTHER API does not work here.
     */
    @Bean
    JwtDecoder jwtDecoder(KeyPair keyPair, AppSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(props.issuer()),
                new JwtAudienceValidator(props.audience())));
        return decoder;
    }

    /**
     * Maps the "roles" claim ["USER","ADMIN"] to ROLE_USER and ROLE_ADMIN. Without this, Spring reads the
     * "scope"/"scp" claim and adds the SCOPE_ prefix, so hasRole('ADMIN') would never match.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
