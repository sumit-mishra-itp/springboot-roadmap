package dev.roadmap.rest.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.roadmap.rest.book.BookController;
import dev.roadmap.rest.book.BookRepository;
import dev.roadmap.rest.book.BookService;
import dev.roadmap.rest.idempotency.IdempotencyStore;

/**
 * Proves where a servlet Filter and a HandlerInterceptor sit relative to the DispatcherServlet.
 * MockMvc runs the real filter chain (Filter beans are part of the @WebMvcTest slice).
 */
@WebMvcTest(BookController.class)
@Import({BookService.class, BookRepository.class, IdempotencyStore.class})
class FilterInterceptorOrderTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void successfulRequest_filterWrapsInterceptor_andBodyIsWrittenBeforePostHandle() {
        MvcTestResult result = mvc.get().uri("/api/books").exchange();

        assertThat(trace(result)).containsExactly(
                "filter.before",
                "interceptor.preHandle",
                // the controller runs here, and the message converter writes its return value BEFORE postHandle.
                // Small body: still buffered, so not committed yet. On real Tomcat a body over the 8 KB buffer IS
                // committed by now, see RestApiApplicationTests#headerAddedInPostHandle_survivesOnly...
                "interceptor.postHandle committed=false",
                "interceptor.afterCompletion ex=null",
                "filter.after");
    }

    @Test
    void failedRequest_skipsPostHandle_andResolvedExceptionIsNotPassedToAfterCompletion() {
        MvcTestResult result = mvc.get().uri("/api/books/424242").exchange();

        assertThat(trace(result)).containsExactly(
                "filter.before",
                "interceptor.preHandle",
                "@ExceptionHandler",                      // HandlerExceptionResolver chain (our advice) runs
                "interceptor.afterCompletion ex=null",    // the exception was handled, so afterCompletion gets null
                "filter.after");
    }

    @Test
    void filter_echoesOrGeneratesRequestId() {
        assertThat(mvc.get().uri("/api/books").header(RequestTraceFilter.REQUEST_ID, "abc-123"))
                .headers().hasValue(RequestTraceFilter.REQUEST_ID, "abc-123");
        assertThat(mvc.get().uri("/api/books")).headers().containsHeader(RequestTraceFilter.REQUEST_ID);
    }

    @SuppressWarnings("unchecked")
    private static List<String> trace(MvcTestResult result) {
        return (List<String>) result.getMvcResult().getRequest().getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE);
    }
}
