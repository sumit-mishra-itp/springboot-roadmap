package dev.roadmap.security.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import dev.roadmap.security.auth.AuthDtos.TokenResponse;
import dev.roadmap.security.config.AppSecurityProperties;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService users;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenStore refreshTokens;
    private final AppSecurityProperties props;

    public AuthService(AuthenticationManager authenticationManager, UserDetailsService users,
            AccessTokenIssuer accessTokens, RefreshTokenStore refreshTokens, AppSecurityProperties props) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.props = props;
    }

    /** Throws BadCredentialsException (an AuthenticationException), which becomes a 401 ProblemDetail. */
    public TokenResponse login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        return tokens(accessTokens.issue(authentication.getName(),
                authentication.getAuthorities()), refreshTokens.startFamily(authentication.getName()));
    }

    /** Roles are re-read on every refresh, so a demoted or disabled user loses access within one access-token TTL. */
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenStore.Rotation rotation = refreshTokens.rotate(refreshToken);
        UserDetails user = users.loadUserByUsername(rotation.username());
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new InvalidRefreshTokenException("Account is disabled or locked");
        }
        return tokens(accessTokens.issue(user.getUsername(), user.getAuthorities()),
                rotation.refreshToken());
    }

    private TokenResponse tokens(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", props.accessTokenTtl().toSeconds());
    }
}
