# Lab 11: Kafka with Spring Kafka 4 (outbox, idempotent consumer, DLT)

Companion to the note [`notes/11-messaging-kafka/kafka.md`](../../notes/11-messaging-kafka/kafka.md).
Stack: Spring Boot 4.1.2, Spring Kafka 4.1.2, kafka-clients 4.2.2, Jackson 3, Flyway, PostgreSQL 16 and
Apache Kafka 4.2.2 (`apache/kafka-native`, KRaft) via Testcontainers 2.0.5, Java 21.

## What it demonstrates

| Package | What |
|---|---|
| `order` | `POST /orders` (`@Valid` record, ProblemDetail errors). `OrderService.placeOrder` saves the `Order` **and** an `OutboxEvent` row (the `OrderPlaced` JSON) in one `@Transactional` method. There is no Kafka call in the request path |
| `outbox` | `OutboxRelay`: a `@Scheduled` poller that locks unsent rows (`FOR UPDATE SKIP LOCKED`), sends them with `KafkaTemplate` (key = `orderId`), waits once for all the `CompletableFuture` acks, and marks them sent in the same transaction. Delivery is at-least-once |
| `inventory` | `@KafkaListener(id = "inventory")` → `InventoryService.reserve`: an **idempotent consumer**. `INSERT ... ON CONFLICT DO NOTHING` into `processed_message` (keyed by `eventId`) and the stock update share one transaction. `SkuNotFoundException` is retryable. `InsufficientStockException` is not |
| `config` | `orders.placed` (3 partitions) and `orders.placed-dlt` topics. A `DefaultErrorHandler` bean (Boot wires it into the container factory) with a `FixedBackOff` from typed `RetryProperties`, a not-retryable exception, and a `DeadLetterPublishingRecoverer` |

`application.properties` sets `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3) behind an `ErrorHandlingDeserializer`, a
logical `__TypeId__` mapping (`orderPlaced`), `acks=all`, `enable.idempotence=true`, `linger.ms=5`, `concurrency=3`, and `ack-mode=batch`.
The transactional producer (`transaction-id-prefix`) is deliberately **not** used. The note (§3.5) explains why the outbox is the right tool for "DB + event".

## Run it

```bash
./mvnw verify                 # Docker must be running: Testcontainers starts Kafka and Postgres
./mvnw test -Dtest=InventoryConsumerTest
./mvnw spring-boot:test-run   # runs the app against Testcontainers Kafka + Postgres
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"sku":"BOOK-1","quantity":2}'
```

## What each test proves

All integration tests extend `IntegrationTest`, so they share one cached context. A second context would add a second
`inventory` group member that steals partitions.

| Test | Proves |
|---|---|
| `order.OrderOutboxTest` | Placing an order writes the order row and the outbox row together. Inside a rolled-back transaction, **neither** row exists. A `POST /orders` is relayed to Kafka (outbox row gets `sent_at`) and the consumer reserves stock (10 → 7). Invalid input returns 400 `application/problem+json` |
| `inventory.InventoryConsumerTest` | The **same event sent twice** is delivered twice but processed once: stock drops once, one `processed_message` row. A **poison event** (unknown SKU, retryable) is attempted exactly 3 times (1 + `max-retries=2`), then lands in `orders.placed-dlt` with `kafka_dlt-exception-fqcn` = `ListenerExecutionFailedException`, `kafka_dlt-exception-cause-fqcn` = `SkuNotFoundException`, the original topic header, and the original key. A **not-retryable** exception goes to the DLT after 1 attempt, and the processed marker is rolled back with the failed stock update. `concurrency=3` creates 3 child containers with 1 partition each |
| `order.PartitionOrderingTest` | 10 events with the same key land in **one partition**, the one `murmur2(key) % 3` predicts, with increasing offsets in send order (read from each send's `CompletableFuture<SendResult>`). The consumer receives them in the same order |

Finding: the `DeadLetterPublishingRecoverer` default destination is **`<topic>-dlt`**, not the `<topic>.DLT` that many tutorials show.
The first run declared `orders.placed.DLT` and the recoverer logged "Destination resolver returned non-existent partition orders.placed-dlt-2".
