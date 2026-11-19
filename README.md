# springboot-roadmap

A hands-on Spring Boot 4 roadmap. It covers a full revision of the core material (up to Spring Security), the advanced topics, AWS integration, and two resume-grade capstone projects. Topics follow [roadmap.sh/spring-boot](https://roadmap.sh/spring-boot). Content is sourced from the [Spring reference docs](https://docs.spring.io/spring-boot/), [Baeldung](https://www.baeldung.com/) and the [Oracle Java docs](https://docs.oracle.com/en/java/javase/21/).

## Stack (pinned 2026-11-18)

| Component | Version |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.1.2 (Spring Framework 7, Jakarta EE 11) |
| Spring Cloud | 2025.1.x "Oakwood" |
| Spring Cloud AWS | 4.x |
| Build | Maven via `./mvnw`, so no local Maven install is needed |
| Local infra | Docker, Testcontainers, LocalStack |

> **LocalStack note:** since March 2026 the LocalStack image needs an auth token. The free **Hobby** plan (non-commercial) covers S3, SQS, SNS and Secrets Manager. Put the token in `.env` as `LOCALSTACK_AUTH_TOKEN=...`. The `.env` file is gitignored.

## Repo layout

```
notes/      deep, self-contained notes, one folder per topic (see notes/_template.md)
labs/       small single-topic Spring Boot apps, one per topic
projects/   two capstone projects (resume-grade)
```

## How to use

1. Read the topic note in `notes/NN-*/`. Each note has diagrams, internals, gotchas, real-world architecture patterns, and interview Q&A.
2. Run the matching lab with `cd labs/NN-* && ./mvnw spring-boot:run`, then break it and fix it.
3. Apply the topic to the capstone feature mapped to it below.
4. Tick the box.

---

## Phase A — Revision

- [x] 00 Java 21 refresher: records, sealed types, pattern matching, streams, Optional, virtual threads
- [x] 01 Spring Core: IoC, DI, bean scopes and lifecycle, `@Configuration`, BeanPostProcessor, events (lab `01-core-ioc`)
- [x] 02 AOP: JDK vs CGLIB proxies, advice, pointcuts, self-invocation (lab `02-aop-logging`)
- [x] 03 Boot internals: starters, auto-configuration, `@Conditional*`, modular starters in Boot 4 (lab `03-custom-starter`)
- [x] 04 Configuration: YAML, profiles, `@ConfigurationProperties`, property precedence
- [x] 05 Web MVC / REST: DispatcherServlet, validation, `ProblemDetail`, API versioning, OpenAPI (lab `05-rest-api`)
- [x] 06 Data JPA / Hibernate: relationships, N+1, projections, transactions, locking, Flyway (lab `06-jpa-deep-dive`)
- [x] 07 Spring Security 7: filter chain, authN/authZ, method security, JWT, OAuth2 (lab `07-security-jwt`)

## Phase B — Advanced

- [x] 08 Testing: slices, `@MockitoBean`, Testcontainers, `@ServiceConnection`, RestTestClient
- [x] 09 Caching + Redis: cache abstraction, TTL, cache-aside, stampede (lab `09-redis-cache`)
- [x] 10 Observability: Actuator, Micrometer, OpenTelemetry, Prometheus/Grafana (lab `10-observability`)
- [x] 11 Messaging with Kafka: retries, DLT, idempotency, outbox (lab `11-kafka`)
- [x] 12 Async, scheduling, Spring Batch, virtual threads (lab `12-batch-csv-import`)
- [x] 13 Reactive: WebFlux, Reactor, R2DBC (lab `13-webflux`)
- [x] 14 Microservices / Spring Cloud: Gateway, Config, Resilience4j, `@HttpExchange`, tracing
- [x] 15 Packaging and deploy: Docker, buildpacks, native image, GitHub Actions, Kubernetes

## Phase C — AWS

- [x] 16 AWS with Spring: S3, SQS, SNS, Secrets Manager, Parameter Store, RDS, LocalStack (lab `16-aws-localstack`)
- [ ] 17 Deploy to AWS: ECR, ECS Fargate, RDS, CloudWatch. Optional, and it costs money. Every note has a teardown checklist.

---

## Capstone 1 — `projects/01-ecommerce-monolith`

A production-style order management API.

- [ ] Products, categories and orders REST API with paging and OpenAPI (05, 06)
- [ ] JWT auth with ADMIN and CUSTOMER roles, refresh tokens, method security (07)
- [ ] Transactional order placement with optimistic locking on stock (06)
- [ ] Redis product cache (09)
- [ ] Product images in S3, DB credentials from Secrets Manager (16)
- [ ] Order-confirmation emails through an SQS consumer (16)
- [ ] Nightly sales report with Spring Batch and `@Scheduled` (12)
- [ ] Actuator and Prometheus metrics (10)
- [ ] Unit, slice and Testcontainers tests (Postgres, Redis, LocalStack) (08)
- [ ] Dockerfile, docker-compose and GitHub Actions CI, with an optional ECS deploy (15, 17)

## Capstone 2 — `projects/02-microservices-platform`

An event-driven order platform with these services: `gateway`, `config-server`, `user-service`, `order-service`, `payment-service`, `notification-service`.

- [ ] Gateway JWT validation with Keycloak (07, 14)
- [ ] `@HttpExchange` clients with a Resilience4j circuit breaker (14)
- [ ] Kafka saga with outbox and DLT (11)
- [ ] SNS to SQS notification fan-out (16)
- [ ] One reactive service using WebFlux and R2DBC (13)
- [ ] Distributed tracing with OpenTelemetry, plus metrics dashboards (10)
- [ ] Config Server, docker-compose for the whole system, Kubernetes manifests (14, 15)
- [ ] Testcontainers integration tests (08)

## Extras

- [`notes/interview-qna.md`](notes/interview-qna.md) collects the interview questions from every note.
