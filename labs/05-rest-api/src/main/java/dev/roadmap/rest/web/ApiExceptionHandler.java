package dev.roadmap.rest.web;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.roadmap.rest.book.BookNotFoundException;
import dev.roadmap.rest.book.StaleVersionException;
import dev.roadmap.rest.idempotency.IdempotencyKeyReusedException;

/**
 * The ONE place errors become RFC 9457 ProblemDetail responses (application/problem+json).
 * Extending ResponseEntityExceptionHandler covers every Spring MVC exception and any ErrorResponse, such as
 * ResponseStatusException or the API-versioning exceptions. The @ExceptionHandler methods add our domain
 * exceptions, and the two overrides add per-field "errors" that the defaults leave out.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String PROBLEM_BASE = "https://api.roadmap.dev/problems/";

    /** Non-standard ProblemDetail property. Jackson renders the properties map as top-level JSON fields. */
    public record Violation(String field, String message) {
    }

    @ExceptionHandler(BookNotFoundException.class)
    ProblemDetail bookNotFound(BookNotFoundException ex, HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, "book-not-found", "Book not found", ex.getMessage());
    }

    @ExceptionHandler(StaleVersionException.class)
    ProblemDetail staleVersion(StaleVersionException ex, HttpServletRequest request) {
        return problem(request, HttpStatus.PRECONDITION_FAILED, "stale-version", "Precondition failed", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException ex, HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency key reused", ex.getMessage());
    }

    /** @Valid @RequestBody failed: one object was validated on its own. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Violation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(Violation::field))
                .toList();
        return validationProblem(ex, ex.getBody(), errors, headers, status, request);
    }

    /** Constraints sit directly on handler parameters (@Min on a @RequestParam), so Spring ran method validation. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        List<Violation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Violation(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .sorted(Comparator.comparing(Violation::field))
                .toList();
        return validationProblem(ex, ex.getBody(), errors, headers, status, request);
    }

    private ResponseEntity<Object> validationProblem(Exception ex, ProblemDetail body, List<Violation> errors, HttpHeaders headers,
                                                     HttpStatusCode status, WebRequest request) {
        body.setType(URI.create(PROBLEM_BASE + "validation-error"));
        body.setTitle("Validation failed");
        body.setDetail(errors.size() + " invalid value(s)");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpServletRequest request, HttpStatus status, String slug, String title, String detail) {
        RequestTraceFilter.record(request, "@ExceptionHandler");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}
