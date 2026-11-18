# Lab 10: Observability with Actuator, Micrometer and OpenTelemetry

Companion to [note 10](../../notes/10-observability/observability.md). Spring Boot 4.1.2, Micrometer 1.17, Micrometer Tracing 1.7, OpenTelemetry SDK 1.62. The tests need no Docker.

## What it demonstrates

| Feature | Where |
|---|---|
| Custom `HealthIndicator` (id `downstream`) in the **readiness** group only, never liveness | `health/DownstreamHealthIndicator`, `application.properties` |
| Kubernetes probe groups `/actuator/health/{liveness,readiness}`, plus `/livez` and `/readyz` on the main port | `application.properties` |
| Custom `InfoContributor`, plus the `env` (`info.*`) and `java` contributors | `info/ObservabilityInfoContributor` |
| Counter, Timer (percentile histogram + SLO buckets), Gauge (strong reference), DistributionSummary (base unit + client-side percentiles) | `orders/OrderService` |
| `@Timed` and `@Counted` (Micrometer aspects) | `OrderService#find` |
| Observation API: `Observation.createNotStarted(...)` with low- and high-cardinality key values. One call gives a timer, a long-task timer and a span | `orders/PricingService` |
| `@Observed` (declarative observation) | `orders/FraudCheck` |
| Tag cardinality guard: `MeterFilter.maximumAllowableTags` | `orders/CardinalityGuard` |
| W3C `traceparent` propagation on a `RestClient` call to itself, using the auto-configured `RestClient.Builder` | `tracing/SelfCallClient`, `tracing/TraceController` |
| Structured ECS JSON logs with `traceId` and `spanId` | `application-otel.properties`, `TracePropagationTest` |
| Log levels changed at runtime through `/actuator/loggers` | `HealthProbesTest` |
| Bean Validation on the request (tag values must never be null), RFC 9457 errors via `spring.mvc.problemdetails.enabled` | `orders/OrderRequest` |
| Dev stack: OTel Collector, Tempo, Prometheus (with alert rules) and Grafana (provisioned datasources) | `compose.yaml`, `compose/` |

## Run it

```bash
./mvnw verify                      # 17 tests, no Docker needed
./mvnw spring-boot:run             # plain run: Prometheus scrape endpoint, tracing on, no export
curl -s -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"channel":"web","tier":"gold","amount":120}'
curl -s localhost:8080/trace/outbound
curl -s localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/prometheus | grep orders_
curl -s -X POST localhost:8080/actuator/loggers/dev.roadmap.observability -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'
```

### Optional: the full stack

The stack is for development only, and starting it is optional. The tests never use it.

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=otel   # OTLP trace export + ECS JSON console logs
# generate some traffic with the curl commands above, then:
#   Grafana     http://localhost:3000  (anonymous admin; Explore > Tempo, Explore > Prometheus)
#   Prometheus  http://localhost:9090  (Status > Targets, Alerts)
docker compose down
```

Data flow: the app pushes traces over OTLP/HTTP to the collector on `:4318`. The collector forwards them to Tempo. Prometheus scrapes `host.docker.internal:8080/actuator/prometheus`. Grafana reads both, and its Prometheus datasource links exemplars to Tempo by `trace_id`. The compose file was validated with `docker compose config` but was not started as part of the build.

## What each test proves

| Test | Proves |
|---|---|
| `HealthProbesTest` (`@SpringBootTest` + `RestTestClient`, no observability annotations) | `/actuator/health` shows `downstream` and the `liveness`/`readiness` groups. When the dependency is down, readiness (and `/readyz`) returns 503, while liveness (and `/livez`) stays 200 and the root health also returns 503. Publishing `ReadinessState.REFUSING_TRAFFIC` (what graceful shutdown does) gives `OUT_OF_SERVICE` and 503 on readiness only. `/actuator/info` shows the custom contributor, `info.*` and Java. `/actuator/loggers` changes a level at runtime, and children inherit it. **Boot 4 test defaults:** `/actuator/prometheus` is 404 without `@AutoConfigureMetrics`. Without `@AutoConfigureTracing`, spans are created locally but the incoming `traceparent` is ignored and none is sent on outgoing calls |
| `MetricsEndpointTest` (`@AutoConfigureMetrics`) | The exact Prometheus names: `orders_placed_total`, `orders_checkout_seconds_bucket{le="0.1"}` (SLO), `orders_amount_usd{quantile="0.95"}`, `orders_in_flight`, `orders_lookup_seconds_count` (`@Timed`), `orders_lookup_calls_total` (`@Counted`), `orders_pricing_seconds_count{tier="gold"}` (Observation), `orders_pricing_active_seconds` (long-task timer), `orders_fraud_check_seconds_count` (`@Observed`), and `http_server_requests` with a templated `uri`. The high-cardinality `order.id` never becomes a tag. With a single registry, the `MeterRegistry` bean is the `PrometheusMeterRegistry` itself, not a composite. A 404 on an unknown path is tagged `uri="/**"` (never the raw path). A body missing `tier` gets a 400 `application/problem+json` from Bean Validation |
| `PricingObservationTest` (plain JUnit, `TestObservationRegistry`) | The observation's name, contextual name, low/high key values, and start/stop, with no Spring context |
| `CardinalityGuardTest` (plain JUnit) | 50 distinct client-supplied `channel` values produce only 5 series |
| `TracePropagationTest` (`@AutoConfigureTracing` + `InMemorySpanExporter`) | An incoming `traceparent` is continued: the same trace id is used, and the outgoing call carries `00-<same trace id>-<client span id>-01`. The exporter receives SERVER, CLIENT and SERVER spans of one trace, and the callee's parent id equals the client span id. Without a header, a new trace starts. The Observation becomes a span `price-order` with `order.id` as an attribute, and the `@Observed` span is `fraud-check`. ECS JSON log lines contain `"traceId"` and `"spanId"`. An unsampled `traceparent` (flag `00`) keeps its trace id downstream and in the logs, with flag `00` |

**Why `reuseForks=false` in the pom:** Logback is initialised once per JVM, and Boot does not re-initialise it for a second cached test context. Giving each test class its own JVM lets `TracePropagationTest` switch the console to ECS JSON.
