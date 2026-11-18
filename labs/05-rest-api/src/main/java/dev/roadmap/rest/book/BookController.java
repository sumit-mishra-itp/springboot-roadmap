package dev.roadmap.rest.book;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import dev.roadmap.rest.idempotency.IdempotencyStore.Outcome;
import dev.roadmap.rest.web.WebConfig;

/**
 * HTTP concerns only: status codes, headers, DTO mapping. No try/catch: exceptions go to ApiExceptionHandler.
 * No class-level @Validated on purpose. The @Min/@Max on parameters trigger Spring MVC's BUILT-IN method
 * validation (Framework 6.1+), which raises HandlerMethodValidationException. @Validated would switch to the
 * AOP proxy path, which throws ConstraintViolationException instead.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    /** 201 + Location. A retry with the same Idempotency-Key replays the original 201 instead of creating twice. */
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request,
                                               @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String key,
                                               UriComponentsBuilder uriBuilder) {
        Outcome<Book> outcome = service.create(request, key);
        Book book = outcome.value();
        URI location = uriBuilder.path("/api/books/{id}").buildAndExpand(book.id()).toUri();
        return ResponseEntity.created(location)
                .eTag(String.valueOf(book.version()))
                .header(IDEMPOTENT_REPLAYED, String.valueOf(outcome.replayed()))
                .body(BookResponse.from(book));
    }

    /**
     * v1: the "1.0" mapping only matches version 1.0. A ResponseEntity carrying an ETag makes Spring answer
     * If-None-Match itself (HttpEntityMethodProcessor), with 304 and no body.
     */
    @GetMapping(path = "/{id}", version = "1.0")
    public ResponseEntity<BookResponse> getV1(@PathVariable long id) {
        Book book = service.find(id);
        return ResponseEntity.ok().eTag(String.valueOf(book.version())).varyBy(WebConfig.API_VERSION).body(BookResponse.from(book));
    }

    /** v2: "2.0+" is a baseline. It matches 2.0 and every supported version above it, until a newer mapping supersedes it. */
    @GetMapping(path = "/{id}", version = "2.0+")
    public ResponseEntity<BookResponseV2> getV2(@PathVariable long id) {
        Book book = service.find(id);
        // ponytail: one ETag per resource version shared by v1 and v2; Vary: API-Version keeps caches apart.
        // Hash the serialized body instead if representations must have distinct strong validators.
        return ResponseEntity.ok().eTag(String.valueOf(book.version())).varyBy(WebConfig.API_VERSION).body(BookResponseV2.from(book));
    }

    /** PUT replaces the whole resource. If-Match turns it into a compare-and-set, and a stale version gets 412. */
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> replace(@PathVariable long id, @Valid @RequestBody BookRequest request,
                                                @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
        Book book = service.replace(id, request, ifMatch == null ? null : parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(String.valueOf(book.version())).body(BookResponse.from(book));
    }

    /** DELETE is idempotent: deleting twice leaves the same state, so both calls return 204. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Offset pagination. Out-of-range params are rejected by built-in method validation, giving 400. */
    @GetMapping
    public PageResponse<BookResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.page(page, size).map(BookResponse::from);
    }

    /** Cursor (keyset) pagination for infinite scroll. Pass back nextCursor until it is null. */
    @GetMapping("/feed")
    public CursorPage<BookResponse> feed(@RequestParam(required = false) @Nullable String cursor,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.feed(cursor == null ? 0 : decodeCursor(cursor), limit).map(BookResponse::from);
    }

    private static long decodeCursor(String cursor) {
        try {
            return Cursor.decode(cursor);
        }
        catch (IllegalArgumentException ex) {
            // ResponseStatusException is an ErrorResponse, so the advice renders it as a ProblemDetail
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed cursor");
        }
    }

    /** ETags are quoted: If-Match: "3". A weak or garbage tag can never match, so it becomes version -1 and gets 412. */
    private static long parseVersion(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", ""));
        }
        catch (NumberFormatException ex) {
            return -1;
        }
    }
}
