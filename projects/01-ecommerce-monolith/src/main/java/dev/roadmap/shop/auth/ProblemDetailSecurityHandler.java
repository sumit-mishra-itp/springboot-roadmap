package dev.roadmap.shop.auth;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import dev.roadmap.shop.common.ApiExceptionHandler;

/**
 * Security errors happen in the filter chain, BEFORE DispatcherServlet, so ApiExceptionHandler never sees them.
 * ExceptionTranslationFilter and BearerTokenAuthenticationFilter call this class instead. That includes an
 * AccessDeniedException thrown by @PreAuthorize inside a controller call: the advice has no handler for it, so it
 * propagates out of DispatcherServlet back to ExceptionTranslationFilter.
 * Not a @Component: SecurityConfig creates it, so @WebMvcTest slices get it by importing SecurityConfig alone.
 */
class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Framework's Jackson 3 converter registers the ProblemDetail mixin, so "properties" is flattened correctly. */
    private final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();

    /** 401 means "who are you?". RFC 6750 says a bearer-protected resource answers with WWW-Authenticate: Bearer. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
    }

    /** 403 means "I know who you are, and you may not". */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to access this resource");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String slug,
                       String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ApiExceptionHandler.PROBLEM_BASE + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        converter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }
}
