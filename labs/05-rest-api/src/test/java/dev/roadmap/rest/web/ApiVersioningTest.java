package dev.roadmap.rest.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.roadmap.rest.book.BookController;
import dev.roadmap.rest.book.BookRepository;
import dev.roadmap.rest.book.BookService;
import dev.roadmap.rest.idempotency.IdempotencyStore;

/** Framework 7 API versioning via the API-Version header (configured in WebConfig#configureApiVersioning). */
@WebMvcTest(BookController.class)
@Import({BookService.class, BookRepository.class, IdempotencyStore.class})
class ApiVersioningTest {

    @Autowired
    MockMvcTester mvc;

    long id;

    @BeforeEach
    void createBook() {
        var created = mvc.post().uri("/api/books").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Effective Java","author":"Joshua Bloch","isbn":"9780134685991","price":45.99}""")
                .exchange();
        String location = created.getResponse().getHeader("Location");
        id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void noVersionHeader_fallsBackToDefault1_0_andAdvertisesDeprecation() {
        assertThat(mvc.get().uri("/api/books/{id}", id))
                .hasStatusOk()
                .headers()
                .hasValue("Deprecation", "@1780272000")                    // RFC 9745 structured date (epoch seconds)
                .hasValue("Sunset", "Tue, 1 Jun 2027 00:00:00 GMT")         // RFC 1123 style, day not zero-padded
                .containsHeader("Link");                                    // rel="deprecation" migration guide
        assertThat(mvc.get().uri("/api/books/{id}", id)).bodyJson().extractingPath("$.price").isEqualTo(45.99);
    }

    @Test
    void version2_0_returnsNewShape_withoutDeprecationHeaders() {
        var result = mvc.get().uri("/api/books/{id}", id).header(WebConfig.API_VERSION, "2.0").exchange();

        assertThat(result).hasStatusOk().doesNotContainHeader("Deprecation");
        // CORS support already adds "Vary: Origin, ...". Our entry is appended so caches key on the version too
        assertThat(result.getResponse().getHeaders("Vary")).contains(WebConfig.API_VERSION);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                        {"price": {"amount": 45.99, "currency": "USD"}}""");
    }

    @Test
    void version2_1_isServedByTheBaseline2_0PlusMapping() {
        // No mapping says "2.1". It is a supported version, and "2.0+" matches 2.0 and every supported version above it.
        assertThat(mvc.get().uri("/api/books/{id}", id).header(WebConfig.API_VERSION, "2.1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.price.currency").isEqualTo("USD");
    }

    @Test
    void unversionedMappings_matchEveryVersion() {
        assertThat(mvc.get().uri("/api/books").header(WebConfig.API_VERSION, "2.0")).hasStatusOk();
    }

    @Test
    void unsupportedVersion_returns400ProblemDetail() {
        // InvalidApiVersionException is a ResponseStatusException, so ResponseEntityExceptionHandler renders it
        var result = mvc.get().uri("/api/books/{id}", id).header(WebConfig.API_VERSION, "9.0").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"title": "Bad Request", "status": 400, "detail": "Invalid API version: '9.0.0'."}""");
        // type defaults to "about:blank", and Spring omits it from the JSON (RFC 9457: absent type means about:blank)
        assertThat(result).bodyJson().doesNotHavePath("$.type");
    }
}
