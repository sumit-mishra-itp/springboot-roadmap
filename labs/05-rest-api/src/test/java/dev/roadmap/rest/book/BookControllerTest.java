package dev.roadmap.rest.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.roadmap.rest.idempotency.IdempotencyStore;
import dev.roadmap.rest.web.ApiExceptionHandler;

/**
 * Web slice: controller + advice + WebMvcConfigurer + Filter beans, but no @Service/@Repository, so the real
 * in-memory ones are imported (cheaper and more honest than mocking a map).
 * MockMvcTester is the AssertJ flavour of MockMvc (Framework 6.2+).
 */
@WebMvcTest(BookController.class)
@Import({BookService.class, BookRepository.class, IdempotencyStore.class})
class BookControllerTest {

    static final String EFFECTIVE_JAVA = """
            {"title":"Effective Java","author":"Joshua Bloch","isbn":"978-0-13-468599-1","price":45.99}""";
    static final String CLEAN_CODE = """
            {"title":"Clean Code","author":"Robert C. Martin","isbn":"9780132350884","price":39.50}""";

    @Autowired
    MockMvcTester mvc;

    @Test
    void create_returns201WithLocationEtagAndBody() {
        MvcTestResult result = post(EFFECTIVE_JAVA);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = idFrom(result);
        assertThat(result).headers()
                .hasValue("Location", "http://localhost/api/books/" + id)
                .hasValue("ETag", "\"1\""); // Spring adds the quotes that RFC 9110 requires
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Effective Java");
    }

    @Test
    void create_withInvalidBody_returnsProblemDetailWithFieldErrors() {
        MvcTestResult result = post("""
                {"title":"","isbn":"978-0-13-468599-2","price":-1}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "type": "https://api.roadmap.dev/problems/validation-error",
                  "title": "Validation failed",
                  "status": 400,
                  "detail": "4 invalid value(s)",
                  "instance": "/api/books",
                  "errors": [
                    {"field": "author", "message": "must not be blank"},
                    {"field": "isbn",   "message": "must be a valid ISBN-13"},
                    {"field": "price",  "message": "must be greater than or equal to 0.00"},
                    {"field": "title",  "message": "must not be blank"}
                  ]
                }""");
    }

    @Test
    void list_withSizeOver100_isRejectedByBuiltInMethodValidation() {
        // @Max on a @RequestParam: HandlerMethodValidationException, not MethodArgumentNotValidException
        assertThat(mvc.get().uri("/api/books?size=500"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"type": "https://api.roadmap.dev/problems/validation-error",
                         "status": 400,
                         "errors": [{"field": "size", "message": "must be less than or equal to 100"}]}""");
    }

    @Test
    void get_unknownId_returns404ProblemDetail() {
        assertThat(mvc.get().uri("/api/books/999999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"type": "%sbook-not-found",
                         "title": "Book not found",
                         "status": 404,
                         "detail": "Book 999999 does not exist",
                         "instance": "/api/books/999999"}""".formatted(ApiExceptionHandler.PROBLEM_BASE));
    }

    @Test
    void conditionalGet_withCurrentEtag_returns304WithoutBody() {
        long id = idFrom(post(EFFECTIVE_JAVA));

        MvcTestResult result = mvc.get().uri("/api/books/{id}", id).header("If-None-Match", "\"1\"").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_MODIFIED);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void put_withStaleIfMatch_returns412() {
        long id = idFrom(post(EFFECTIVE_JAVA));

        assertThat(put(id, "\"1\"")).hasStatus(HttpStatus.OK).headers().hasValue("ETag", "\"2\"");
        assertThat(put(id, "\"1\""))                                   // a second writer still holding version 1
                .hasStatus(HttpStatus.PRECONDITION_FAILED)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "stale-version");
    }

    @Test
    void post_withSameIdempotencyKey_replaysInsteadOfCreatingTwice() {
        MvcTestResult first = postWithKey("key-replay", EFFECTIVE_JAVA);
        MvcTestResult retry = postWithKey("key-replay", EFFECTIVE_JAVA);

        assertThat(first).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "false");
        assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(idFrom(retry)).isEqualTo(idFrom(first));
    }

    @Test
    void post_reusingIdempotencyKeyWithDifferentBody_returns422() {
        postWithKey("key-reuse", EFFECTIVE_JAVA);

        assertThat(postWithKey("key-reuse", CLEAN_CODE))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "idempotency-key-reused");
    }

    @Test
    void delete_isIdempotent() {
        long id = idFrom(post(EFFECTIVE_JAVA));

        assertThat(mvc.delete().uri("/api/books/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/books/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/books/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD) // fresh, empty repository for exact counts
    void list_returnsOffsetPage() {
        post(EFFECTIVE_JAVA);
        post(CLEAN_CODE);
        post(EFFECTIVE_JAVA);

        assertThat(mvc.get().uri("/api/books?page=1&size=2"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"page": 1, "size": 2, "totalElements": 3, "totalPages": 2,
                         "content": [{"id": 3}]}""");
    }

    @Test
    @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
    void feed_walksPagesWithOpaqueCursor() throws Exception {
        post(EFFECTIVE_JAVA);
        post(CLEAN_CODE);
        post(EFFECTIVE_JAVA);

        MvcTestResult first = mvc.get().uri("/api/books/feed?limit=2").exchange();
        assertThat(first).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(1, 2);
        String cursor = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");

        assertThat(mvc.get().uri("/api/books/feed?limit=2&cursor={c}", cursor))
                .bodyJson().isLenientlyEqualTo("""
                        {"items": [{"id": 3}], "nextCursor": null}""");
    }

    @Test
    void feed_withMalformedCursor_returns400Problem() {
        assertThat(mvc.get().uri("/api/books/feed?cursor=not-a-cursor"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("Malformed cursor");
    }

    private MvcTestResult post(String json) {
        return mvc.post().uri("/api/books").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult postWithKey(String key, String json) {
        return mvc.post().uri("/api/books").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(long id, String ifMatch) {
        return mvc.put().uri("/api/books/{id}", id).header("If-Match", ifMatch)
                .contentType(MediaType.APPLICATION_JSON).content(CLEAN_CODE).exchange();
    }

    private static long idFrom(MvcTestResult result) {
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
