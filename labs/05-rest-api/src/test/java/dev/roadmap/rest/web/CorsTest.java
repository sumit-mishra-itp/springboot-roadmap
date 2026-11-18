package dev.roadmap.rest.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.roadmap.rest.book.BookController;
import dev.roadmap.rest.book.BookRepository;
import dev.roadmap.rest.book.BookService;
import dev.roadmap.rest.idempotency.IdempotencyStore;

@WebMvcTest(BookController.class)
@Import({BookService.class, BookRepository.class, IdempotencyStore.class})
class CorsTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void preflight_fromAllowedOrigin_isApproved_evenWithoutApiVersionHeader() {
        assertThat(mvc.options().uri("/api/books")
                .header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Idempotency-Key, Content-Type"))
                .hasStatusOk()
                .headers()
                .hasValue("Access-Control-Allow-Origin", "https://app.example.com")
                .hasValue("Access-Control-Max-Age", "3600");
    }

    @Test
    void preflight_fromUnknownOrigin_isRejected() {
        assertThat(mvc.options().uri("/api/books")
                .header("Origin", "https://evil.example.org")
                .header("Access-Control-Request-Method", "POST"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader("Access-Control-Allow-Origin");
    }

    @Test
    void actualRequest_exposesCustomHeadersToBrowserJs() {
        assertThat(mvc.get().uri("/api/books").header("Origin", "https://app.example.com"))
                .hasStatusOk()
                .headers().containsHeader("Access-Control-Expose-Headers");
    }
}
