# Lab 12: Spring Batch CSV import, scheduling, @Async and virtual threads

Companion to the note [`notes/12-async-batch/async-scheduling-batch.md`](../../notes/12-async-batch/async-scheduling-batch.md).
Stack: Spring Boot 4.1.2, Spring Batch 6.0.6, Spring Framework 7.0.10, Flyway, PostgreSQL 16 (Testcontainers 2), Java 21.

## What it demonstrates

| Package | What |
|---|---|
| `importer` | `transactionImportJob`: a **partitioned** step (one worker per CSV file, `MultiResourcePartitioner`) runs a chunk-oriented worker step (`FlatFileItemReader` → validating `TransactionProcessor` → `JdbcBatchItemWriter`, chunk size 10), then a **tasklet** step deletes the imported files. Fault tolerance: skip `FlatFileParseException` and `InvalidTransactionException` (skip limit 10), retry `TransientDataAccessException` (3 attempts). `ImportStepListener` logs step metrics and skips and returns the exit code `COMPLETED WITH SKIPS` |
| `schedule` | `ImportScheduler`: a 6-field `cron` (nightly at 02:00) and a `fixedDelay` inbox poll, both launching the job through `JobOperator` with a `run.id` job parameter |
| `async` | `@EnableAsync`, a bounded `reportExecutor` (`ThreadPoolTaskExecutor`, `CallerRunsPolicy`), `MdcTaskDecorator`, an `AsyncUncaughtExceptionHandler`, and `ReportGenerator`, which fans out with `CompletableFuture` (`orTimeout`, `exceptionally`, `allOf`) |

Schema: Flyway owns both the Batch metadata tables (`V1__spring_batch_schema.sql`, copied from `spring-batch-core`) and
the `bank_transaction` table (`V2`). `spring.batch.jdbc.initialize-schema=never` and `spring.batch.job.enabled=false`.

The two `chaos.*` job parameters (`chaos.transient-fail-id`, `chaos.crash-on-id`) are lab-only fault injection, so the
tests can prove retry and restart against real infrastructure.

## Run it

```bash
./mvnw verify                 # Docker must be running: Testcontainers starts postgres:16-alpine
./mvnw test -Dtest=ImportJobTest
./mvnw spring-boot:test-run   # runs the app against a Testcontainers Postgres. Drop CSVs into ./inbox
```

## What each test proves

| Test | Kind | Proves |
|---|---|---|
| `importer.ImportJobTest` | `@SpringBootTest` + `@SpringBatchTest`, Testcontainers | 2 files → 2 partitions, 40 rows, 3 commits for 25 items with chunk size 10, cleanup tasklet deletes the files. One bad line is a **read skip**, a bad amount or currency is a **process skip**, a zero amount is **filtered**, and the listener sets `COMPLETED WITH SKIPS`. The 11th skip fails the job. A transient error is retried with no chunk rollback and a 1 s default back-off. **Restart**: a crash at item 25 leaves 20 committed rows from that file (plus a second, clean file's partition). Re-launching with the same identifying parameters reuses the same `JobInstance`, does not re-run the completed partition, reads only items 21–35, and ends with no duplicates |
| `async.AsyncTest` | `ApplicationContextRunner` (no DB) | `@Async` returns an incomplete `CompletableFuture` at once and runs on `report-*`. Self-invocation runs on the caller thread. Plain `@Async` uses Boot's `app-async-*` pool, or **virtual threads** with `spring.threads.virtual.enabled=true`, while the custom pool stays on platform threads. Boot applies the `TaskDecorator` bean, so the MDC reaches both executors and virtual threads. A `void` method's exception reaches the handler. The report fan-out runs its sections concurrently on distinct threads and degrades a failed section. With no executor bean at all, `@Async` falls back to a new `SimpleAsyncTaskExecutor` thread per call |
| `async.MdcTaskDecoratorTest` | plain JUnit | The MDC (a `ThreadLocal`) is lost on a pool thread without the decorator, is present with it, and is cleared again afterwards |
| `schedule.ImportSchedulerTest` | `ApplicationContextRunner` + mocked `JobOperator` | Cron and fixedDelay both fire, both on the single `scheduling-1` thread. A 50 ms `fixedRate` task that takes 200 ms never overlaps itself, even with `pool.size=4`, but does overlap on the virtual-thread scheduler |
| `schedule.CronSyntaxTest` | plain JUnit | Spring cron has 6 fields (seconds first) and rejects 5-field Unix cron. Covers `@daily`, `L`, `MON-FRI`, `MON#1`, and `"-"` = disabled |
