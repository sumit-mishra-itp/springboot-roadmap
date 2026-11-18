package dev.roadmap.reactive.web;

import java.net.URI;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

/**
 * One RFC 9457 error path for BOTH styles. A @RestControllerAdvice only sees exceptions from annotated
 * controllers. Errors from RouterFunctions and WebFilters skip it and land in the WebExceptionHandler chain.
 * <p>
 * Order -2 runs before Boot's DefaultErrorWebExceptionHandler (-1). Anything that isn't an ErrorResponse
 * (an unexpected bug) falls through to Boot's handler, which answers 500 without leaking a stack trace.
 */
@Component
@Order(-2)
public class ProblemDetailExceptionHandler implements WebExceptionHandler {

    private final ServerCodecConfigurer codecs;

    public ProblemDetailExceptionHandler(ServerCodecConfigurer codecs) {
        this.codecs = codecs;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof ErrorResponse error) || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);   // not ours: pass it to the next handler in the chain
        }
        ProblemDetail problem = error.getBody();
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        if (ex instanceof WebExchangeBindException binding) {
            problem.setProperty("errors", binding.getFieldErrors().stream()
                    .map(field -> field.getField() + ": " + field.getDefaultMessage())
                    .sorted()
                    .toList());
        }
        return ServerResponse.status(error.getStatusCode())
                .headers(headers -> headers.addAll(error.getHeaders()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, new WritersOnly()));
    }

    private class WritersOnly implements ServerResponse.Context {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return codecs.getWriters();
        }

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    }
}
