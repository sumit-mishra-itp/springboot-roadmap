# Lab 02: Spring AOP, proxies & aspects

Companion lab for [note 02](../../notes/02-aop/spring-aop.md). It's a Boot 4.1 / Framework 7 app with no web layer. Every AOP claim in the note is proven here by a test, using a shared `CallLog` recorder that aspects and targets append to.

## What it demonstrates

| Package | Concept |
|---|---|
| `logging` | `@Loggable` custom annotation plus an `@Around` timing aspect that binds the annotation (`@annotation(loggable)`) |
| `audit` + `transfer` | A second aspect (`@Audited`, a compliance trail). `@Order(1)` audit wraps `@Order(2)` logging |
| `ordering` | All five advice types in one aspect, on a method that returns and one that throws |
| `selfinvocation` | The `this.method()` trap, the `ObjectProvider` self-injection fix, the `AopContext.currentProxy()` fix, and a `final` method that silently escapes advice |
| `payment` | CGLIB vs JDK proxies: Boot's CGLIB default, Framework 7 `@Proxyable(INTERFACES)`, plain-Framework JDK default, and the `this`/`target`/`bean`/`args` designators |
| `inventory` | Framework 7 `@Retryable` on a flaky downstream client, the programmatic `RetryTemplate`, and a retry-only bean (`EmailStockNotifier`) that shows the advising post-processor following the CGLIB default |
| `export` | Framework 7 `@ConcurrencyLimit` with the `REJECT` and `BLOCK` policies |

`AopConfig` turns on `exposeProxy` and `@EnableResilientMethods`. Boot doesn't enable the resilience annotations for you.

## Run it

```bash
./mvnw verify                               # 23 tests
./mvnw test -Dtest=AdviceOrderTest          # a single class
```

There's no Docker and no external services. The AOP starter is `spring-boot-starter-aspectj`, which Boot 4 renamed from `spring-boot-starter-aop`.

## What each test proves

| Test | Proves |
|---|---|
| `LoggingAspectTest` | A `@Loggable` method runs inside the around advice, and the return value passes through unchanged |
| `AdviceOrderTest` | Order inside one aspect. Success: `around:before, before, target, afterReturning, after, around:after`. Failure: `... target, afterThrowing, after, around:caught` |
| `AspectOrderTest` | Across aspects, the lower `@Order` value is outermost (first in, last out) |
| `SelfInvocationTest` | `this.render()` gets no advice. The self-proxy and `AopContext` fixes do. A `final` method runs on the empty CGLIB proxy instance, so its field is `null` and no advice fires |
| `ProxyTypeTest` | In Boot, a bean that implements an interface is still a CGLIB proxy (`AopUtils.isCglibProxy`), and `@Proxyable(INTERFACES)` flips a single bean to a JDK proxy |
| `AdvisingProcessorProxyTypeTest` | Framework 7 applies the global CGLIB default to the advising post-processors too. A bean with only `@Retryable` (one advisor, built by the retry post-processor alone) is still a CGLIB proxy |
| `PlainFrameworkProxyTest` | Without Boot, `@EnableAspectJAutoProxy` gives a JDK proxy for the same bean |
| `DesignatorTest` | On a JDK proxy, `target(Impl)` matches but `this(Impl)` doesn't. `bean(card*)` matches by name, and `args(..)` binds the argument |
| `RetryableTest` | 2 failures then success = 3 attempts. Exhaustion = 1 + `maxRetries` attempts and the *original* exception. Exceptions outside `includes` aren't retried. Retry wraps the custom aspect, so logging runs once per attempt |
| `RetryTemplateTest` | `invoke` returns after transient failures. `execute` throws a checked `RetryException` that holds every attempt's exception |
| `ConcurrencyLimitTest` | `REJECT` throws `InvocationRejectedException` for a second concurrent caller. `BLOCK` makes callers wait, so at most 1 is ever in flight |
