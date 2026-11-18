# Lab 06: Spring Data JPA & Hibernate deep dive

Companion to the note [`notes/06-data-jpa/jpa-hibernate.md`](../../notes/06-data-jpa/jpa-hibernate.md).
Stack: Spring Boot 4.1.2, Hibernate ORM 7.4, Spring Data JPA 4.1, Flyway 12, PostgreSQL 16 (Testcontainers 2).

The application itself does nothing at runtime. **The tests are the lab**: each one proves a single JPA,
Hibernate or Spring transaction behaviour against a real Postgres, mostly by counting SQL statements
with Hibernate's `Statistics` API.

## Domain

```
Customer 1 ── N PurchaseOrder 1 ── N OrderLine N ── 1 Product N ── 1 Category
                                                      (@Version, @SoftDelete)
AuditLog (IDENTITY id)          Tag (deliberately broken equals/hashCode)
```

- Schema: Flyway `src/main/resources/db/migration/V1__schema.sql`. Hibernate runs with `ddl-auto=validate`.
- `open-in-view=false`, `hibernate.jdbc.batch_size=20`, `order_inserts/updates=true`, `generate_statistics=true`.

## Run it

```bash
./mvnw verify            # Docker must be running: Testcontainers starts postgres:16-alpine
./mvnw test -Dtest=NPlusOneTest
./mvnw spring-boot:run   # optional: Docker Compose support starts compose.yaml's Postgres, Flyway migrates it
```

`@DataJpaTest` prints every SQL statement (`showSql` is on by default in the slice), so read the test
output side by side with the assertions.

## What each test proves

| Test class | Kind | Proves |
|---|---|---|
| `NPlusOneTest` | `@DataJpaTest` | A lazy `@OneToMany` in a loop costs 1 + N statements. A lazy `@ManyToOne` costs 1 + (distinct parents). `proxy.getId()` costs nothing. `JOIN FETCH` and `@EntityGraph` do it in 1 statement and Hibernate 6+ de-duplicates roots without `DISTINCT`. `@BatchSize(5)` loads 10 collections in 2 statements |
| `DefaultBatchFetchSizeTest` | `@DataJpaTest` + property | `hibernate.default_batch_fetch_size=16` turns the same N+1 code into 2 statements globally |
| `PersistenceContextTest` | `@DataJpaTest` | Same id → same instance and 1 SQL. A JPQL query always hits the DB but returns the managed instance. Dirty checking writes an UPDATE at flush without `save()`. SEQUENCE ids defer the INSERT, IDENTITY ids force it immediately. `FlushModeType.AUTO` flushes before a query, `COMMIT` does not, and a native query auto-flushes too. Flush runs INSERTs before DELETEs, so replacing a unique key in one flush fails. 40 SEQUENCE inserts take ≤ 4 statements (batched), 40 IDENTITY inserts take 40. An id-based `hashCode` loses the entity in a `HashSet` after `persist()` |
| `QueryFeaturesTest` | `@DataJpaTest` | Interface projections return proxies, record projections return DTOs, `select new` builds aggregates. `Page` = 2 statements, but the COUNT is skipped on a partial last page. `Slice` = 1 statement. Keyset `Window` scrolling walks 2+2+1 and stays correct when an earlier row is deleted (OFFSET would skip a row). `@Modifying` bulk UPDATE leaves managed entities stale, `clearAutomatically` fixes that, and bulk JPQL does not bump `@Version` |
| `MappingFeaturesTest` | `@DataJpaTest` | `@CreatedDate`/`@LastModifiedDate` auditing. `@SoftDelete` turns delete into an UPDATE, hides the row from JPA but not from native SQL. A LAZY `@ManyToOne` to a `@SoftDelete` entity is loaded eagerly anyway. `orphanRemoval` deletes a line removed from the collection |
| `customer.TransactionBehaviourTest` | `@SpringBootTest` | `REQUIRES_NEW` commit survives the outer rollback, `REQUIRED` does not. Catching an inner failure gives `UnexpectedRollbackException`. `MANDATORY`/`NEVER` throw `IllegalTransactionStateException`, and `NESTED` is not supported by `JpaTransactionManager` by default. Checked exceptions commit by default, `rollbackFor` changes that, runtime exceptions roll back. Dirty checking without `save()`. `readOnly=true` never flushes and Postgres rejects writes. Self-invocation bypasses the proxy. Package-private `@Transactional` methods ARE intercepted. `LazyInitializationException` outside a transaction and its fix (`@EntityGraph`) |
| `catalog.LockingTest` | `@SpringBootTest` | Saving a stale `@Version` copy throws `ObjectOptimisticLockingFailureException` (no lost update). A flash sale (10 buyers, 5 items) never oversells with optimistic locking + `RetryTemplate`, nor with `PESSIMISTIC_WRITE`. A held `PESSIMISTIC_WRITE` lock (Hibernate 7.4 emits `FOR NO KEY UPDATE`) makes a NOWAIT lock attempt fail with `PessimisticLockingFailureException` |

Integration tests commit for real, so they truncate all tables in `@BeforeEach`. Slice tests roll back.
The two test meta-annotations (`@JpaSliceTest`, `@IntegrationTest`) keep the number of Spring contexts,
and therefore Postgres containers, to three.
