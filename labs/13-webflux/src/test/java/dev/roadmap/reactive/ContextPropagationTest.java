package dev.roadmap.reactive;

import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/** ThreadLocal vs Reactor Context, and the Micrometer bridge between them. */
class ContextPropagationTest {

    @Test
    void plainThreadLocal_isLostAfterAThreadHop() {
        ThreadLocal<String> requestId = new ThreadLocal<>();
        requestId.set("req-42");
        try {
            Mono<String> seen = Mono.just("x")
                    .publishOn(Schedulers.boundedElastic())
                    .map(x -> String.valueOf(requestId.get()));

            StepVerifier.create(seen).expectNext("null").verifyComplete();
        } finally {
            requestId.remove();
        }
    }

    @Test
    void reactorContext_isVisibleOnlyUpstreamOfContextWrite() {
        Mono<String> readsBelow = Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("requestId", "missing")))
                .contextWrite(Context.of("requestId", "req-42"));
        Mono<String> readsAbove = Mono.just("x")
                .contextWrite(Context.of("requestId", "req-42"))
                .flatMap(x -> Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("requestId", "missing"))));

        StepVerifier.create(readsBelow).expectNext("req-42").verifyComplete();
        StepVerifier.create(readsAbove).expectNext("missing").verifyComplete();   // the context flows from subscriber to source
    }

    @Test
    void automaticContextPropagation_restoresMdcFromTheContext_onEveryThread() {
        ContextRegistry.getInstance().registerThreadLocalAccessor("requestId",
                () -> MDC.get("requestId"), value -> MDC.put("requestId", value), () -> MDC.remove("requestId"));
        Hooks.enableAutomaticContextPropagation();   // what spring.reactor.context-propagation=auto does at startup
        try {
            Mono<String> seen = Mono.just("x")
                    .publishOn(Schedulers.boundedElastic())
                    .map(x -> String.valueOf(MDC.get("requestId")))
                    .contextWrite(Context.of("requestId", "req-42"));

            StepVerifier.create(seen).expectNext("req-42").verifyComplete();
        } finally {
            Hooks.disableAutomaticContextPropagation();
        }
    }
}
