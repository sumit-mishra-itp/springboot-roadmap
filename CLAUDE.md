# CLAUDE.md

This is a Spring Boot 4 learning repo. It holds deep notes, small labs, and two capstone projects. The stack is Java 21, Spring Boot 4.1.x, Spring Cloud 2025.1.x, Spring Cloud AWS 4.x, and Maven.

## Commands
Every project under `labs/` and `projects/` is standalone and has its own `mvnw`. Run commands from inside the project directory. Maven is not installed globally.
- Build and run all tests: `./mvnw verify`
- View the notes site from the repo root: `java -m jdk.httpserver -d "$PWD\notes" -p 3000`, then open http://localhost:3000. Use `npx docsify-cli serve notes` for live reload.
- Run a single test: `./mvnw test -Dtest=OrderServiceTest` (single method: `-Dtest=OrderServiceTest#placesOrder`)
- Run the app: `./mvnw spring-boot:run`
- Start local infra: `docker compose up -d`
- Integration tests use Testcontainers, so Docker must be running.
- LocalStack needs `LOCALSTACK_AUTH_TOKEN` set in `.env`, which is gitignored and never committed.

## Repo layout & workflow
- `notes/NN-topic/`: one `.md` per topic. Every note follows `notes/_template.md` and fills all 10 sections, including at least one Mermaid diagram and at least one real-life architecture example.
- `notes/` is also a Docsify site: `index.html` plus `_sidebar.md`. Add every new note to `_sidebar.md`. Link to labs and projects with absolute GitHub URLs (`https://github.com/sumit-mishra-itp/springboot-roadmap/tree/main/labs/...`) so the links work both on GitHub and on the site.
- `labs/NN-topic/`: one standalone app per topic. Create it with Spring Initializr when the phase starts, not before:
  `curl https://start.spring.io/starter.zip -d type=maven-project -d bootVersion=<current 4.x> -d javaVersion=21 -d dependencies=... -o lab.zip`
- `projects/`: the two capstones. Each is self-contained (its own mvnw and README, no shared parent pom) so it can be split into its own repo later.
- After each note, lab, or capstone milestone: tick its box in the root `README.md`, append the note's interview questions to `notes/interview-qna.md`, then commit and push to `origin main`. Use one logical unit per commit and a conventional message such as `notes(06): JPA deep dive`, `lab(09): redis cache-aside`, or `feat(ecommerce): order placement`.
- Don't create folders or files for phases that haven't started.

## Writing notes
- Notes must be self-contained. A reader should never have to open the source docs to understand them. Cover the internals, the niche details, and the gap fillers.
- Source Boot 4 and Framework 7 changes from the spring.io release notes or migration guide, not from memory. Many Baeldung articles describe 3.x, so call out outdated advice explicitly.
- Every note ends with source links: docs.spring.io, baeldung.com, and docs.oracle.com.
- Use Mermaid for diagrams (GitHub renders it): `sequenceDiagram` for flows, `flowchart` for architecture, `classDiagram` for hierarchies.

## Spring conventions
- Use constructor injection with `final` fields. No field `@Autowired`.
- Prefer typed, validated config with `@ConfigurationProperties` (records plus `@Validated`) over scattered `@Value`.
- Organize code package-by-feature (`order/`, `product/`), not package-by-layer.
- Controllers handle only HTTP concerns and call services. They never call repositories directly.
- Use Java `record` DTOs at the API boundary. Never serialize JPA entities. Map with plain methods; add MapStruct only once mapping becomes painful.
- Validate with `@Valid` on request bodies and Bean Validation on DTOs. Return errors as RFC 9457 `ProblemDetail` from one `@RestControllerAdvice`. Don't add try/catch blocks in controllers.
- Use resource-based REST paths (`/orders/{id}`, never `/getOrder`), correct status codes, and pagination for every collection endpoint.
- Put `@Transactional` on service methods. Use `readOnly = true` for queries. Never put it on private methods or on code reached through self-invocation, because the proxy won't apply.
- JPA:
  - Use `LAZY` for all associations.
  - Fix N+1 with `@EntityGraph` or a fetch join.
  - Set `spring.jpa.open-in-view=false`.
  - Manage schema with Flyway. Use `ddl-auto=validate`, except `update` is allowed in throwaway labs.
  - Don't use Lombok `@Data` on entities. Write `equals`/`hashCode` on the id or business key.
  - Use `@Version` for optimistic locking wherever concurrent updates matter.
- Security:
  - Build the `SecurityFilterChain` bean with the lambda DSL.
  - Keep stateless JWT for APIs.
  - Never log tokens or passwords.
  - Keep secrets in env vars or AWS Secrets Manager, never in `application.yml` committed to git.
- Use `spring.threads.virtual.enabled=true` for blocking I/O apps where it makes sense. Know the pinning caveats documented in note 12.

## Clean code
- Keep methods small and single-purpose. Use intention-revealing names. Use early returns over nested `if`s.
- Don't add speculative abstractions. An interface with a single implementation is only allowed when the lesson is about interfaces or proxies, or a framework requires it. Don't create factories for one product or config for values that never change.
- Use Java 21 idioms: records, sealed interfaces with pattern-matching `switch`, `var` for obvious local types, and `Optional` only as a return type.
- Don't commit commented-out code, `System.out.println`, or swallowed exceptions. Use SLF4J with parameterized messages.
- Avoid Lombok in labs, so the generated boilerplate stays visible while learning. Records cover most of it anyway.

## Testing
- Every lab and capstone feature ships with tests, and `./mvnw verify` must pass before committing. When reporting that tests pass, show the command and its output.
- Pick the smallest test that proves the behavior:
  - plain JUnit and Mockito for logic;
  - `@WebMvcTest` for controllers;
  - `@DataJpaTest` with Testcontainers Postgres for repositories;
  - `@SpringBootTest` only for end-to-end flows.
- Use Testcontainers with `@ServiceConnection` for Postgres, Redis, Kafka, and LocalStack. Don't use H2 as a stand-in for Postgres.
- Mock beans with `@MockitoBean`. `@MockBean` was removed in Boot 4.
- Name tests by behavior, e.g. `placesOrder_whenStockAvailable`, and follow given/when/then.

## Gotchas
- Lifecycle facts must be proven by a test before they go in a note. An example claim that turned out wrong: "@PostConstruct runs before custom BeanPostProcessors" (see lab 01 `LifecycleOrderTest`).
- `python` is not installed. Use PowerShell `Invoke-RestMethod` / `ConvertFrom-Json` for JSON.
- Boot 4 uses Jackson 3, whose packages are `tools.jackson.*`. Annotations stay in `com.fasterxml.jackson.annotation`.
- Boot 4 has modular starters, so some auto-configs that used to come transitively now need an explicit starter (for example `spring-boot-starter-webmvc-test` for MVC test slices). Check the migration guide when a slice or auto-config seems to be missing.
- In Boot 4, `@SpringBootTest` no longer provides MockMvc, `TestRestTemplate`, or `WebTestClient`. Add `@AutoConfigureMockMvc` or `@AutoConfigureRestTestClient`, and prefer `RestTestClient` over `TestRestTemplate`.
