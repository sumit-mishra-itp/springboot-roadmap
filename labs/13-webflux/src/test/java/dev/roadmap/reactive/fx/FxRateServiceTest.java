package dev.roadmap.reactive.fx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class FxRateServiceTest {

    private final FxRateService fx = new FxRateService();

    @Test
    void blockingLookup_runsOnBoundedElastic_neverOnTheCallerThread() {
        StepVerifier.create(fx.usdTo("EUR").map(rate -> Thread.currentThread().getName() + "|" + Schedulers.isInNonBlockingThread()))
                .assertNext(seen -> assertThat(seen).startsWith("boundedElastic-").endsWith("|false"))
                .verifyComplete();
    }

    @Test
    void unknownCurrency_isAnEmptyResult_turnedIntoA400() {
        StepVerifier.create(fx.usdTo("XYZ"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .verify();
    }
}
