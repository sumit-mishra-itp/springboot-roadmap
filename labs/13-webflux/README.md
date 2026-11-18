# Lab 13: Reactive Spring (WebFlux, Reactor, R2DBC)

Companion to the note [`notes/13-reactive/webflux.md`](../../notes/13-reactive/webflux.md).
Stack: Spring Boot 4.1.2, Framework 7.0.10, Reactor Core 3.8.8 (Reactor BOM 2025.0.x), Reactor Netty 1.3.8, Netty 4.2,
Spring Data R2DBC 4.1, r2dbc-postgresql 1.1, Flyway 12.4, PostgreSQL 16 (Testcontainers 2).

## What it demonstrates

| Package | Feature |
|---|---|
| `price` | **Annotated controller** (`PriceController`), an R2DBC record entity with a `ReactiveCrudRepository`, a paged derived query, `TransactionalOperator` for an all-or-nothing batch, and an **SSE** stream (`GET /prices/stream?symbol=X`) fed by a hot `Sinks.many().multicast().directBestEffort()` with a per-client `onBackpressureBuffer(256, DROP_OLDEST)` and heartbeats |
| `quote` | **Functional endpoints** (`QuoteRoutes` + `QuoteHandler`). `QuoteClient` calls a slow downstream with `WebClient`: per-attempt `timeout`, `onErrorMap` (404 to a domain error), `retryWhen(Retry.backoff)` for transient errors only, then an `onErrorResume` fallback to the last known quote marked `stale`. `GET /quotes/{symbol}?currency=EUR` fans out with `Mono.zip` (BFF style) |
| `fx` | A blocking lookup wrapped with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` |
| `web` | `RequestIdFilter` (a `WebFilter` that writes the request id into the Reactor `Context`, and a Micrometer context-propagation accessor that mirrors it into the SLF4J MDC) and `ProblemDetailExceptionHandler` (one global `WebExceptionHandler` that renders every `ErrorResponse` as RFC 9457 `application/problem+json`, for both controller and functional styles) |

**Why Flyway runs on JDBC:** Flyway has no R2DBC support. `spring-boot-starter-flyway` pulls `spring-boot-starter-jdbc`, and the
`postgresql` JDBC driver is a runtime dependency. `@ServiceConnection` on the Postgres container produces both R2DBC and
JDBC connection details. Boot's `DataSourceAutoConfiguration` backs off when an R2DBC `ConnectionFactory` exists, so no
Hikari pool is created: Flyway opens its own short-lived JDBC connection at startup, and the app itself only uses R2DBC.
The alternative, `spring.sql.init` with `schema.sql` over R2DBC, has no versioning, so it only suits throwaway demos.

**BlockHound** (`io.projectreactor.tools:blockhound`) is not added. It instruments the JDK and throws when a blocking call
such as `Thread.sleep` runs on a non-blocking thread. On JDK 13+ it needs `-XX:+AllowRedefinitionToAddDeleteMethods`.
`FxRateServiceTest` proves the same point more cheaply by asserting the thread name.

## Run it

```bash
./mvnw verify              # Docker must be running: Testcontainers starts postgres:16-alpine
./mvnw test -Dtest=ReactorOperatorsTest
./mvnw spring-boot:test-run   # runs TestWebfluxApplication: the app plus a Testcontainers Postgres
curl -N "localhost:8080/prices/stream?symbol=ACME"     # terminal 1: SSE, heartbeat comments every 15 s
curl -XPOST localhost:8080/prices -H 'Content-Type: application/json' -d '{"symbol":"ACME","amount":101.5}'   # terminal 2
curl "localhost:8080/quotes/ACME?currency=EUR"          # calls the simulated slow downstream (0-1 s latency, 500 ms timeout)
```

## What each test proves (46 tests)

| Test class | Kind | Proves |
|---|---|---|
| `ReactorOperatorsTest` (19) | plain JUnit + `StepVerifier` | Nothing runs until subscribe, and cold publishers re-run per subscriber. `Mono.just(x)` evaluates `x` at assembly. `switchIfEmpty(...)` builds its fallback eagerly unless wrapped in `Mono.defer`. With inners delayed by n×100 ms, `flatMap` emits 1,2,3 after 300 ms, `concatMap` emits 3,1,2 after 600 ms, and `flatMapSequential` emits 3,1,2 after 300 ms (virtual time). `zip` takes max latency and completes **empty** if a source is empty. `merge` interleaves by time, `concat` keeps source order. An error ends the sequence even after `onErrorResume`, while recovering inside `flatMap` skips one element. `onErrorMap` wraps. `Retry.backoff` resubscribes at 1 s then 3 s and wraps exhaustion in `RetryExhaustedException`. `share()` runs upstream once and late subscribers miss elements. `cache()` replays the value **and errors**. `subscribeOn` moves the source, `publishOn` moves what is below it |
| `BackpressureTest` (4) | plain JUnit | `blockLast()` requests `Long.MAX_VALUE`. `limitRate(10)` requests 10, then 8 at a time (75% replenish). `onBackpressureBuffer(3, DROP_OLDEST)` keeps 8,9,10. The default strategy fails with an overflow error |
| `ContextPropagationTest` (3) | plain JUnit | A `ThreadLocal` is lost after `publishOn`. The Reactor `Context` is only visible upstream of `contextWrite`. With `Hooks.enableAutomaticContextPropagation()` and a registered `ThreadLocalAccessor`, the MDC is restored on the new thread |
| `fx.FxRateServiceTest` (2) | plain JUnit | The blocking call runs on a `boundedElastic-*` thread. An unknown currency (empty) becomes a 400 via `switchIfEmpty` |
| `quote.QuoteClientTest` (4) | WebClient + JDK `HttpServer` | 503, 503, 200 succeeds after 3 calls. 404 maps to `UnknownSymbolException` with exactly 1 call (not retried). A slow downstream times out on each of 3 attempts, then serves the cached quote as `stale`. No cache gives 503 |
| `ApiWebLayerTest` (8) | `@WebFluxTest` + `WebTestClient` | 201 + `Location`. Invalid body gives a 400 ProblemDetail with an `errors` list, and the service is never called. A 404 ProblemDetail. `size=500` fails method validation. The functional route zips quote and FX. A functional-route error is rendered by the global `WebExceptionHandler`. The request id is echoed when safe and replaced when it isn't. The request id appears as `[rid=abc-123]` in a log line written on a `boundedElastic` thread. The slice omits `ReactorAutoConfiguration`, so the test imports it to apply `spring.reactor.context-propagation=auto` |
| `price.PriceIntegrationTest` (6) | `@SpringBootTest(RANDOM_PORT)` + Testcontainers | Flyway schema plus a paged derived query (newest first), with no `DataSource` bean in the context. Saving a record returns a **new** instance with the id. POST then GET over real Netty. `TransactionalOperator` rolls back the first INSERT when the second violates a CHECK constraint. Cancelling a transactional import after the first row also rolls it back. The SSE stream pushes a newly recorded price to a connected client |
