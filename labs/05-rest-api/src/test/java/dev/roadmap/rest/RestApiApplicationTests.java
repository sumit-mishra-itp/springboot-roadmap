package dev.roadmap.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.roadmap.rest.book.BookRequest;
import dev.roadmap.rest.book.BookResponse;
import dev.roadmap.rest.book.BookResponseV2;
import dev.roadmap.rest.book.PageResponse;

/**
 * End to end over real HTTP on embedded Tomcat. In Boot 4, @SpringBootTest no longer gives you a client, so
 * @AutoConfigureRestTestClient provides RestTestClient (the blocking twin of WebTestClient) bound to the random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RestApiApplicationTests {

    static final BookRequest EFFECTIVE_JAVA =
            new BookRequest("Effective Java", "Joshua Bloch", "9780134685991", new BigDecimal("45.99"));

    @Autowired
    RestTestClient client;

    @Value("${local.server.port}")
    int port;

    @Test
    void retriedPostWithIdempotencyKey_createsExactlyOneBook() {
        long before = totalBooks();
        String key = UUID.randomUUID().toString();

        BookResponse first = createWithKey(key);
        BookResponse retry = createWithKey(key); // e.g. the client timed out and retried

        assertThat(retry).isEqualTo(first);
        assertThat(totalBooks()).isEqualTo(before + 1);
    }

    @Test
    void restClientWithApiVersionInserter_getsV2Representation() {
        long id = createWithKey(UUID.randomUUID().toString()).id();
        // Framework 7 client-side versioning: the inserter writes the version wherever the server expects it
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .apiVersionInserter(ApiVersionInserter.useHeader("API-Version"))
                .build();

        BookResponseV2 v2 = restClient.get().uri("/api/books/{id}", id).apiVersion("2.0")
                .retrieve().body(BookResponseV2.class);

        assertThat(v2.price()).isEqualTo(new BookResponseV2.Money(new BigDecimal("45.99"), "USD"));
    }

    @Test
    void tomcatHandlesRequestsOnVirtualThreads_whenEnabled() {
        client.get().uri("/test/thread").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.virtual").isEqualTo(true);
    }

    @Test
    void headerAddedInPostHandle_survivesOnlyWhileTheBodyStillFitsInTomcatsBuffer() {
        // The body is written inside the HandlerAdapter, BEFORE postHandle. A small body still sits in Tomcat's
        // response buffer (8 KB by default), so the response is not committed and a late header still gets out...
        client.get().uri("/api/books").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Post-Handle", "added");
        // ...but a body larger than the buffer forced a flush, so the headers were already sent and the late
        // header is silently dropped. Do not rely on postHandle for @ResponseBody. Use preHandle, a Filter,
        // or ResponseBodyAdvice instead.
        client.get().uri("/api/probe/large").exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Post-Handle");
    }

    @Test
    void classLevelValidated_turnsAParameterViolationInto500() {
        // BookController (no @Validated) answers a bad @Max param with a 400 ProblemDetail (see BookControllerTest).
        // Here the AOP proxy throws ConstraintViolationException, which ResponseEntityExceptionHandler does not map.
        client.get().uri("/test/validated?n=0").exchange()
                .expectStatus().isEqualTo(500);
    }

    @Test
    void openApiDocs_includeTheBooksApi() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.paths['/api/books/{id}'].get").exists();
    }

    private BookResponse createWithKey(String key) {
        return client.post().uri("/api/books")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(EFFECTIVE_JAVA)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BookResponse.class)
                .returnResult().getResponseBody();
    }

    private long totalBooks() {
        return client.get().uri("/api/books").exchange()
                .expectStatus().isOk()
                .expectBody(PageResponse.class)
                .returnResult().getResponseBody().totalElements();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ThreadProbeConfig {

        @Bean
        ThreadProbe threadProbe() {
            return new ThreadProbe();
        }

        @Bean
        ValidatedProbe validatedProbe() {
            return new ValidatedProbe();
        }

        /** Tries to add a header in postHandle, the classic mistake with @RestController. */
        @Bean
        WebMvcConfigurer lateHeaderInterceptor() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                                               @Nullable ModelAndView modelAndView) {
                            response.setHeader("X-Post-Handle", "added");
                        }
                    }).addPathPatterns("/api/**");
                }
            };
        }
    }

    /**
     * The pre-6.1 recipe: class-level @Validated. Validation now goes through the MethodValidationPostProcessor AOP
     * proxy instead of Spring MVC's built-in method validation.
     */
    @Validated
    @RestController
    static class ValidatedProbe {

        @GetMapping("/test/validated")
        public int echo(@RequestParam @Min(1) int n) {
            return n;
        }
    }

    /** Test-only endpoints. */
    @RestController
    static class ThreadProbe {

        /** Reports which kind of thread Tomcat used for the request. */
        @GetMapping("/test/thread")
        Map<String, Object> thread() {
            return Map.of("virtual", Thread.currentThread().isVirtual(), "name", Thread.currentThread().toString());
        }

        /** 20 KB body: bigger than Tomcat's default 8 KB response buffer. */
        @GetMapping("/api/probe/large")
        String large() {
            return "x".repeat(20_000);
        }
    }
}
