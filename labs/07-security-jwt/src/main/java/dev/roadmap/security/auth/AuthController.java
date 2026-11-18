package dev.roadmap.security.auth;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.roadmap.security.auth.AuthDtos.LoginRequest;
import dev.roadmap.security.auth.AuthDtos.Me;
import dev.roadmap.security.auth.AuthDtos.RefreshRequest;
import dev.roadmap.security.auth.AuthDtos.TokenResponse;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Shows what the JwtAuthenticationConverter produced from the token. */
    @GetMapping("/api/me")
    public Me me(Authentication authentication) {
        return new Me(authentication.getName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
    }
}
