# Lab 05: Spring MVC REST API

A `/api/books` REST API on Spring Boot 4.1 / Framework 7, backed by an in-memory store (no database, no Docker). It goes with [note 05](../../notes/05-web-mvc-rest/rest-apis.md).

## What it demonstrates

| Concept | Where |
|---|---|
| CRUD with record DTOs, `201 Created` + `Location`, idempotent `DELETE` | `book/BookController` |
| Bean Validation plus a custom constraint (`@Isbn`, ISBN-13 checksum) | `book/BookRequest`, `book/Isbn`, `book/IsbnValidator` |
| `MethodArgumentNotValidException` (`@Valid @RequestBody`) vs `HandlerMethodValidationException` (`@Max` on a `@RequestParam`) | `BookController#create` vs `#list` |
| RFC 9457 `ProblemDetail` from one `@RestControllerAdvice`, with an `errors` array of field errors | `web/ApiExceptionHandler` |
| Offset pagination (`PageResponse`) and cursor/keyset pagination (`CursorPage`, opaque cursor) | `BookController#list`, `#feed` |
| ETag + conditional GET (`If-None-Match` gives 304), `If-Match` on PUT (412 on a stale version) | `BookController#getV1`, `#replace` |
| Framework 7 API versioning: `API-Version` header, `1.0` vs `2.0+`, default version, deprecation headers | `web/WebConfig#configureApiVersioning` |
| Servlet `Filter` (correlation id) vs `HandlerInterceptor` | `web/RequestTraceFilter`, `web/RequestTraceInterceptor` |
| Global CORS with exposed headers | `web/WebConfig#addCorsMappings`, `web/CorsProperties` |
| Stripe-style `Idempotency-Key` on POST | `idempotency/IdempotencyStore` |
| Virtual threads on Tomcat | `application.properties` |
| springdoc OpenAPI UI (springdoc 3.x is the Boot 4 line) | http://localhost:8080/swagger-ui.html |

## Run it

```bash
./mvnw verify            # 32 tests
./mvnw spring-boot:run   # then:
curl -i -X POST localhost:8080/api/books -H 'Content-Type: application/json' -H 'Idempotency-Key: k1' \
  -d '{"title":"Effective Java","author":"Joshua Bloch","isbn":"9780134685991","price":45.99}'
curl -i localhost:8080/api/books/1                          # v1 (default) + Deprecation/Sunset headers
curl -i localhost:8080/api/books/1 -H 'API-Version: 2.0'    # v2 shape
curl -i localhost:8080/api/books/1 -H 'If-None-Match: "1"'  # 304
```

## What each test proves

| Test | Proves |
|---|---|
| `BookControllerTest` (`@WebMvcTest` + `MockMvcTester`) | 201 + `Location` + quoted `ETag`. The exact ProblemDetail JSON (`type`, `title`, `status`, `detail`, `instance`, `errors`) for body validation and for parameter method validation. 404 ProblemDetail. 304 on `If-None-Match`. 412 on a stale `If-Match`. Idempotency replay (same id, `Idempotent-Replayed: true`) and 422 on key reuse with a different body. `DELETE` twice gives 204 both times. Offset page maths. Cursor walk. 400 on a malformed cursor |
| `ApiVersioningTest` | No header falls back to the default `1.0` and gets the `Deprecation`/`Sunset`/`Link` headers. `2.0` returns the new shape with `Vary: API-Version`. `2.1` (no mapping of its own) is served by the `2.0+` baseline. Unversioned mappings match every version. `9.0` gives a 400 ProblemDetail, and its `type` is omitted because it is `about:blank` |
| `FilterInterceptorOrderTest` | Order: `filter.before → preHandle → postHandle → afterCompletion → filter.after`. On an exception, `postHandle` is skipped, the `@ExceptionHandler` runs, and `afterCompletion` receives `ex=null` because the exception was resolved |
| `CorsTest` | A preflight from the allowed origin is approved (with no `API-Version` header needed). An unknown origin gets 403. Custom headers are exposed to browser JS |
| `IdempotencyStoreTest` (plain JUnit) | 32 concurrent retries with one key run the action exactly once. A different fingerprint is rejected. A failed action is not stored, so the client can retry |
| `RestApiApplicationTests` (`@SpringBootTest` + `RestTestClient`, real Tomcat) | Over real HTTP, a retried POST creates one book. `RestClient` + `ApiVersionInserter` fetches v2. Tomcat runs requests on **virtual threads**. A header set in `postHandle` survives only while the body still fits Tomcat's 8 KB buffer (it is dropped for a 20 KB body). Class-level `@Validated` turns a bad `@RequestParam` into a 500. `/v3/api-docs` documents the API |
