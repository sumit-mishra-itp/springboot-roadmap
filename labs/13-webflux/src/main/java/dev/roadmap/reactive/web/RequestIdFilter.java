package dev.roadmap.reactive.web;

import java.util.UUID;
import java.util.regex.Pattern;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Puts a request id into the Reactor Context (not a ThreadLocal: one request hops across many threads).
 * With {@code spring.reactor.context-propagation=auto}, Micrometer's context-propagation copies it into
 * SLF4J's MDC around every operator, so log lines carry it on whichever thread they run.
 */
@Component
public class RequestIdFilter implements WebFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String CONTEXT_KEY = "requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");   // it ends up in logs: no log injection

    public RequestIdFilter() {
        // Teaches the global ContextRegistry how to move "requestId" between the Reactor Context and the MDC.
        // (Micrometer's Slf4jThreadLocalAccessor would NOT do: it stores a whole MDC map under the key "cp.slf4j".)
        ContextRegistry.getInstance().registerThreadLocalAccessor(CONTEXT_KEY,
                () -> MDC.get(CONTEXT_KEY), value -> MDC.put(CONTEXT_KEY, value), () -> MDC.remove(CONTEXT_KEY));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        // contextWrite affects everything UPSTREAM of it, i.e. the whole chain: filters, controller, WebClient calls.
        return chain.filter(exchange).contextWrite(Context.of(CONTEXT_KEY, requestId));
    }
}
