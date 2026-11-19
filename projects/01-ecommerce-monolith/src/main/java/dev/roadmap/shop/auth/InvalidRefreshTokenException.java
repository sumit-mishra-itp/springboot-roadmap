package dev.roadmap.shop.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * An AuthenticationException, so when it escapes the controller (ApiExceptionHandler has no handler for it),
 * ExceptionTranslationFilter turns it into a 401 ProblemDetail through the AuthenticationEntryPoint.
 */
class InvalidRefreshTokenException extends AuthenticationException {

    InvalidRefreshTokenException(String message) {
        super(message);
    }
}
