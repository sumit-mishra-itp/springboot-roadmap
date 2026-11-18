package dev.roadmap.security.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * An AuthenticationException, so when it escapes the controller ExceptionTranslationFilter turns it into a
 * 401 through our AuthenticationEntryPoint. No extra @ExceptionHandler is needed.
 */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
