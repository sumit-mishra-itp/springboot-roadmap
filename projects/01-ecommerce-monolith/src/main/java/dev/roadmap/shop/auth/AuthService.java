package dev.roadmap.shop.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.roadmap.shop.auth.AuthDtos.TokenResponse;
import dev.roadmap.shop.auth.AuthDtos.UserResponse;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenStore refreshTokens;
    private final AuthProperties props;

    AuthService(AuthenticationManager authenticationManager, UserAccountRepository users, PasswordEncoder passwordEncoder,
                AccessTokenIssuer accessTokens, RefreshTokenStore refreshTokens, AuthProperties props) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.props = props;
    }

    /** Always a CUSTOMER. A taken username fails the UNIQUE constraint at commit and becomes a 409 (no check-then-insert race). */
    @Transactional
    public UserResponse register(String username, String password) {
        return UserResponse.from(users.save(new UserAccount(username, passwordEncoder.encode(password), Role.CUSTOMER)));
    }

    /** A wrong password throws BadCredentialsException (an AuthenticationException), which becomes a 401 ProblemDetail. */
    @Transactional
    public TokenResponse login(String username, String password) {
        var user = (AuthenticatedUser) authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password)).getPrincipal();
        return tokens(accessTokens.issue(user.id(), user.username(), user.role()), refreshTokens.startFamily(user.id()));
    }

    /**
     * noRollbackFor: on reuse, RefreshTokenStore revokes the token family AND throws. A rollback would undo the
     * revocation and leave the thief's newest token alive. AuthApiTest proves the revocation survives.
     * The role is re-read on every refresh, so a role change takes effect within one access-token TTL.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenStore.Rotation rotation = refreshTokens.rotate(refreshToken);
        UserAccount user = users.findById(rotation.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        return tokens(accessTokens.issue(user.getId(), user.getUsername(), user.getRole()), rotation.refreshToken());
    }

    private TokenResponse tokens(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", props.accessTokenTtl().toSeconds());
    }
}
