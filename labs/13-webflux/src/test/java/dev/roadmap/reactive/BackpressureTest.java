package dev.roadmap.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import reactor.core.Exceptions;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

/** Backpressure is the Subscription.request(n) signal travelling upstream. These tests watch it. */
class BackpressureTest {

    @Test
    void blockLast_requestsUnbounded() {
        List<Long> requests = new CopyOnWriteArrayList<>();

        Flux.range(1, 40).doOnRequest(requests::add).blockLast();

        assertThat(requests).containsExactly(Long.MAX_VALUE);   // "give me everything": no backpressure at all
    }

    @Test
    void limitRate_requestsInBatches_andReplenishesAt75Percent() {
        List<Long> requests = new CopyOnWriteArrayList<>();

        Flux.range(1, 40).doOnRequest(requests::add).limitRate(10).blockLast();

        assertThat(requests.getFirst()).isEqualTo(10L);
        assertThat(requests.subList(1, requests.size())).isNotEmpty().allMatch(n -> n == 8L);   // 10 - 10/4
    }

    // onBackpressureBuffer requests UNBOUNDED from upstream, then holds what the subscriber has not asked for yet.

    @Test
    void onBackpressureBuffer_dropOldest_keepsTheNewestElements() {
        TestPublisher<Integer> producer = TestPublisher.create();
        StepVerifier.create(producer.flux().onBackpressureBuffer(3, dropped -> { }, BufferOverflowStrategy.DROP_OLDEST), 0)
                .expectSubscription()
                .then(() -> producer.next(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).complete())   // pushed while the subscriber requested 0
                .thenRequest(10)
                .expectNext(8, 9, 10)   // 1..7 were dropped while the subscriber requested nothing
                .verifyComplete();
    }

    @Test
    void onBackpressureBuffer_defaultStrategy_failsWithOverflow() {
        TestPublisher<Integer> producer = TestPublisher.create();
        StepVerifier.create(producer.flux().onBackpressureBuffer(3), 0)
                .expectSubscription()
                .then(() -> producer.next(1, 2, 3, 4, 5).complete())
                .thenRequest(10)
                .thenConsumeWhile(n -> true)
                .expectErrorMatches(Exceptions::isOverflow)
                .verify();
    }
}
