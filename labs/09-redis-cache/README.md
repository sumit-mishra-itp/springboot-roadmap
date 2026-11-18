# Lab 09: Spring cache abstraction on Redis

Companion to [note 09](../../notes/09-caching-redis/caching-redis.md). Spring Boot 4.1.2, Spring Data Redis 4.1.2, Lettuce 7.5.2, Jackson 3, Testcontainers 2.

## What it demonstrates

| Feature | Where |
|---|---|
| Cache-aside with `@Cacheable`, key SpEL (`#id`, `#sku.toUpperCase()`), the default `SimpleKey` for two parameters | `catalog/ProductService` |
| `condition` (checked before the call) vs `unless` (checked after, against `#result`) | `ProductService#quote`, `#findBySku` |
| Null caching (negative cache) vs `unless = "#result == null"` | `#findBySkuCachingMisses` vs `#findBySku` |
| `@CacheEvict` (delete-after-write) and `@Caching` (put + evict) | `#updatePrice`, `#rename` |
| `sync = true` stampede protection | `#findById` |
| The self-invocation bypass | `#priceLabel` |
| Our own `RedisCacheManager`: per-cache TTL from typed properties, TTL jitter via `TtlFunction`, `catalog:` key prefix, Jackson 3 `GenericJacksonJsonRedisSerializer` with a type allow-list, locking + immediate-write `RedisCacheWriter`, `SCAN` clears, and unknown cache names rejected | `config/CacheConfig`, `config/CatalogCacheProperties` |
| Fixed-window rate limiter: `INCR` + `PEXPIRE` in one Lua script, applied by a `HandlerInterceptor`, 429 + `Retry-After` as RFC 9457 `ProblemDetail` | `ratelimit/*`, `web/ApiExceptionHandler` |
| A slow "database" with a call counter (200 ms per call) | `catalog/ProductRepository` |

## Run it

```bash
./mvnw verify            # 19 tests, needs Docker (Testcontainers starts redis:7-alpine)
./mvnw spring-boot:run   # spring-boot-docker-compose starts compose.yaml's Redis and wires it in
curl -s localhost:8080/products/1                      # ~200 ms (miss), then fast (hit)
curl -s "localhost:8080/products/1/quote?currency=EUR"
curl -s -X PUT localhost:8080/products/1/price -H 'Content-Type: application/json' -d '{"priceCents":3900}'
docker compose exec redis redis-cli --scan --pattern 'catalog:*'
```

Docker Compose support stops the Redis container when the app stops.

## What each test proves

| Test | Proves |
|---|---|
| `CacheBehaviourTest` | A hit skips the repository, and the Redis key is `catalog:products::1`. `@CacheEvict` removes the entry on update and the next read reloads it. `@CachePut` writes the result so the next read needs no reload, while `@Caching` also evicts the SKU-index entry. The service is a CGLIB proxy, and a self-call skips it. Key SpEL normalizes case. `unless` leaves misses uncached, and the negative cache stores them. The default key is stored as `SimpleKey [1, EUR]`, and `condition` false skips the cache |
| `CacheExpiryTest` | Each cache gets its own TTL plus up to 10% jitter (checked with `PTTL`). An entry expires and the next call reloads it |
| `StampedeTest` | With the locking writer, 8 concurrent misses run the loader **once**. Without `sync`, they all hit the DB. **On Boot's default non-locking writer, `Cache.get(key, loader)` (what `sync = true` calls) does not collapse misses.** The lock is per cache, so a load in progress for key 2 also stalls a hit on key 1 |
| `SerializationTest` | The record is stored as JSON with an `@class` hint and reads back from Redis as a `Product`. An unknown cache name returns `null` instead of creating an untuned cache |
| `RateLimitTest` | 3 requests per 2 s are allowed and the 4th is rejected, with a per-client counter that resets when the key expires. Over HTTP the 4th call gets 429 `application/problem+json` with `Retry-After`. An unknown product returns 404 `ProblemDetail` |
