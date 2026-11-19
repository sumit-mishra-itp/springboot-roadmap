package dev.roadmap.shop.common;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
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

import dev.roadmap.shop.catalog.InsufficientStockException;

/**
 * The ONE place errors become RFC 9457 ProblemDetail (application/problem+json). Extending
 * ResponseEntityExceptionHandler covers every Spring MVC exception (bad JSON, 405, 415, ...). The handlers below add
 * the domain and persistence cases, and the overrides add a per-field "errors" list the defaults leave out.
 * Boot's own ProblemDetailsExceptionHandler backs off because this bean is a ResponseEntityExceptionHandler.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String PROBLEM_BASE = "https://api.shopsphere.dev/problems/";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record Violation(String field, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    /** Unique key (duplicate SKU or category name) or foreign key (category still has products). The DB is the judge. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException ex) {
        // The root cause names the constraint. Log it, but never echo SQL back to the client.
        log.info("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", "The request conflicts with existing data (duplicate value or resource still in use)");
    }

    /** @Version check failed: another transaction changed the row first. The client should re-read and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Concurrent update", "The resource was modified by another request. Re-read it and retry.");
    }

    /**
     * Not enough stock for the order. A different slug from concurrent-update on purpose: retrying this one is
     * pointless, while a concurrent-update is worth one retry.
     */
    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "insufficient-stock", "Insufficient stock", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        return problem;
    }

    /** ?sort=bogus: Spring Data cannot resolve the property on the entity. A client error, not a 500. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail badSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-sort", "Invalid sort property", "Unknown sort property '" + ex.getPropertyName() + "'");
    }

    /** @Valid @RequestBody failed. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Violation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(Violation::field).thenComparing(Violation::message))
                .toList();
        return validationProblem(ex, ex.getBody(), errors, headers, status, request);
    }

    /** Constraints directly on handler parameters (@Positive on a @RequestParam): Spring MVC's built-in method validation. */
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

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}
