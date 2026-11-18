package dev.roadmap.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

/** Plain Reactor, no Spring. Each test pins down one "what happens when" fact quoted in the note. */
class ReactorOperatorsTest {

    // ---------- assembly time vs subscription time ----------

    @Test
    void nothingHappensUntilSubscribe_andColdPublishersRerunPerSubscriber() {
        AtomicInteger calls = new AtomicInteger();
        Mono<Integer> recipe = Mono.fromCallable(calls::incrementAndGet).map(n -> n * 10);

        assertThat(calls).hasValue(0);   // assembled, not executed

        StepVerifier.create(recipe).expectNext(10).verifyComplete();
        StepVerifier.create(recipe).expectNext(20).verifyComplete();
        assertThat(calls).hasValue(2);   // cold: each subscriber triggers the work again
    }

    @Test
    void monoJust_evaluatesItsArgumentAtAssemblyTime() {
        AtomicInteger calls = new AtomicInteger();

        Mono<Integer> eager = Mono.just(calls.incrementAndGet());   // plain Java: the argument runs NOW

        assertThat(calls).hasValue(1);
        StepVerifier.create(eager).expectNext(1).verifyComplete();
        StepVerifier.create(eager).expectNext(1).verifyComplete();
        assertThat(calls).hasValue(1);
    }

    @Test
    void switchIfEmpty_buildsItsFallbackEagerly_unlessDeferred() {
        AtomicInteger fallbackBuilt = new AtomicInteger();

        StepVerifier.create(Mono.just("hit").switchIfEmpty(expensiveFallback(fallbackBuilt))).expectNext("hit").verifyComplete();
        assertThat(fallbackBuilt).hasValue(1);   // built although never used

        StepVerifier.create(Mono.just("hit").switchIfEmpty(Mono.defer(() -> expensiveFallback(fallbackBuilt)))).expectNext("hit").verifyComplete();
        assertThat(fallbackBuilt).hasValue(1);   // defer: not built

        StepVerifier.create(Mono.<String>empty().switchIfEmpty(Mono.just("fallback"))).expectNext("fallback").verifyComplete();
        StepVerifier.create(Mono.<String>empty().defaultIfEmpty("default")).expectNext("default").verifyComplete();
    }

    private static Mono<String> expensiveFallback(AtomicInteger counter) {
        counter.incrementAndGet();
        return Mono.just("fallback");
    }

    // ---------- flatMap vs concatMap vs flatMapSequential ----------

    /** Each inner publisher completes after n * 100 ms. */
    private static Mono<Integer> delayed(int n) {
        return Mono.just(n).delayElement(Duration.ofMillis(n * 100L));
    }

    @Test
    void flatMap_subscribesEagerly_andEmitsInCompletionOrder() {
        StepVerifier.withVirtualTime(() -> Flux.just(3, 1, 2).flatMap(ReactorOperatorsTest::delayed))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(300))   // all three ran concurrently: done after the slowest (300 ms)
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    void concatMap_subscribesOneAtATime_andKeepsSourceOrder() {
        StepVerifier.withVirtualTime(() -> Flux.just(3, 1, 2).concatMap(ReactorOperatorsTest::delayed))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(300)).expectNext(3)
                .expectNoEvent(Duration.ofMillis(99))   // 1 only starts after 3 finished
                .thenAwait(Duration.ofMillis(1)).expectNext(1)
                .thenAwait(Duration.ofMillis(200)).expectNext(2)   // total 600 ms = sum of the inners
                .verifyComplete();
    }

    @Test
    void flatMapSequential_subscribesEagerly_butReordersToSourceOrder() {
        StepVerifier.withVirtualTime(() -> Flux.just(3, 1, 2).flatMapSequential(ReactorOperatorsTest::delayed))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(300))   // concurrent like flatMap: 300 ms total
                .expectNext(3, 1, 2)                 // ordered like concatMap
                .verifyComplete();
    }

    // ---------- zip and merge ----------

    @Test
    void zip_runsSourcesConcurrently_andWaitsForTheSlowest() {
        StepVerifier.withVirtualTime(() -> Mono.zip(
                        Mono.just(1).delayElement(Duration.ofSeconds(2)),
                        Mono.just("a").delayElement(Duration.ofSeconds(1))))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(1999))
                .thenAwait(Duration.ofMillis(1))     // 2 s, not 3 s
                .expectNext(Tuples.of(1, "a"))
                .verifyComplete();
    }

    @Test
    void zip_withAnEmptySource_completesEmpty() {
        StepVerifier.create(Mono.zip(Mono.just(1), Mono.empty())).verifyComplete();   // no value, no error: easy to miss
    }

    @Test
    void merge_interleavesByArrivalTime_whileConcatKeepsSourceOrder() {
        StepVerifier.withVirtualTime(() -> Flux.merge(ticks("slow", 300), ticks("fast", 100)))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("fast0", "fast1", "slow0", "slow1")
                .verifyComplete();

        StepVerifier.withVirtualTime(() -> Flux.concat(ticks("slow", 300), ticks("fast", 100)))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("slow0", "slow1", "fast0", "fast1")
                .verifyComplete();
    }

    private static Flux<String> ticks(String name, long periodMillis) {
        return Flux.interval(Duration.ofMillis(periodMillis)).take(2).map(i -> name + i);
    }

    // ---------- errors and retries ----------

    @Test
    void anErrorTerminatesTheSequence_evenWhenRecovered() {
        StepVerifier.create(Flux.just(1, 2, 0, 4).map(n -> 10 / n).onErrorResume(e -> Flux.just(-1)))
                .expectNext(10, 5, -1)   // 4 is never processed: onErrorResume swaps in a NEW sequence
                .verifyComplete();
    }

    @Test
    void recoveringInsideFlatMap_skipsOnlyTheFailedElement() {
        StepVerifier.create(Flux.just(1, 0, 2).flatMap(n -> Mono.fromCallable(() -> 10 / n).onErrorResume(e -> Mono.empty())))
                .expectNext(10, 5)
                .verifyComplete();
    }

    @Test
    void onErrorMap_translatesTheExceptionType() {
        StepVerifier.create(Mono.fromCallable(() -> 10 / 0).onErrorMap(ArithmeticException.class, e -> new IllegalStateException("bad input", e)))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(ArithmeticException.class))
                .verify();
    }

    @Test
    void retryWhenBackoff_resubscribesAfterGrowingDelays_inVirtualTime() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> flaky = Mono.defer(() -> attempts.incrementAndGet() < 3
                ? Mono.error(new IllegalStateException("boom"))
                : Mono.just("ok"));

        StepVerifier.withVirtualTime(() -> flaky.retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).jitter(0)))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(1))   // retry #1 at t=1s (fails)
                .thenAwait(Duration.ofSeconds(2))       // retry #2 at t=3s: delay doubled to 2s
                .expectNext("ok")
                .verifyComplete();
        assertThat(attempts).hasValue(3);   // retry RE-SUBSCRIBES: the whole Mono.defer runs again
    }

    @Test
    void retryWhenBackoff_wrapsTheLastErrorWhenExhausted() {
        StepVerifier.withVirtualTime(() -> Mono.error(new IllegalStateException("boom")).retryWhen(Retry.backoff(2, Duration.ofSeconds(1))))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(10))
                .expectErrorSatisfies(e -> {
                    assertThat(Exceptions.isRetryExhausted(e)).isTrue();
                    assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
                })
                .verify();
    }

    // ---------- cold vs hot ----------

    @Test
    void share_isHot_lateSubscribersMissEarlierElements_andUpstreamRunsOnce() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        AtomicInteger upstreamSubscriptions = new AtomicInteger();
        Flux<Long> hot = Flux.interval(Duration.ofSeconds(1), clock).take(3)
                .doOnSubscribe(s -> upstreamSubscriptions.incrementAndGet())
                .share();
        List<Long> early = new ArrayList<>();
        List<Long> late = new ArrayList<>();

        hot.subscribe(early::add);
        clock.advanceTimeBy(Duration.ofMillis(1500));   // 0 already emitted
        hot.subscribe(late::add);
        clock.advanceTimeBy(Duration.ofSeconds(5));

        assertThat(early).containsExactly(0L, 1L, 2L);
        assertThat(late).containsExactly(1L, 2L);
        assertThat(upstreamSubscriptions).hasValue(1);
    }

    @Test
    void withoutShare_eachSubscriberGetsItsOwnUpstream() {
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        AtomicInteger upstreamSubscriptions = new AtomicInteger();
        Flux<Long> cold = Flux.interval(Duration.ofSeconds(1), clock).take(3)
                .doOnSubscribe(s -> upstreamSubscriptions.incrementAndGet());
        List<Long> late = new ArrayList<>();

        cold.subscribe();
        clock.advanceTimeBy(Duration.ofMillis(1500));
        cold.subscribe(late::add);
        clock.advanceTimeBy(Duration.ofSeconds(5));

        assertThat(late).containsExactly(0L, 1L, 2L);   // replayed from the start: its own timer
        assertThat(upstreamSubscriptions).hasValue(2);
    }

    @Test
    void cache_runsOnce_andReplaysTheValue_toLateSubscribers() {
        AtomicInteger calls = new AtomicInteger();
        Mono<Integer> cached = Mono.fromCallable(calls::incrementAndGet).cache();

        StepVerifier.create(cached).expectNext(1).verifyComplete();
        StepVerifier.create(cached).expectNext(1).verifyComplete();
        assertThat(calls).hasValue(1);
    }

    @Test
    void cache_alsoCachesErrors() {
        AtomicInteger calls = new AtomicInteger();
        Mono<Integer> cached = Mono.<Integer>fromCallable(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("downstream down");
        }).cache();

        StepVerifier.create(cached).expectError(IllegalStateException.class).verify();
        StepVerifier.create(cached).expectError(IllegalStateException.class).verify();
        assertThat(calls).hasValue(1);   // the failure is replayed forever: use cache(ttlValue, ttlError, ttlEmpty)
    }

    // ---------- threading ----------

    @Test
    void subscribeOn_movesTheSource_publishOn_movesEverythingBelowIt() {
        List<String> threads = new ArrayList<>();

        Mono.just("x")
                .doOnNext(x -> threads.add(Thread.currentThread().getName()))   // source side: subscribeOn's thread
                .publishOn(Schedulers.parallel())
                .doOnNext(x -> threads.add(Thread.currentThread().getName()))   // below publishOn: parallel
                .subscribeOn(Schedulers.boundedElastic())                       // position does not matter
                .block();

        assertThat(threads.get(0)).startsWith("boundedElastic-");
        assertThat(threads.get(1)).startsWith("parallel-");
    }
}
