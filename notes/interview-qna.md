# Interview Q&A Bank

This file collects the interview questions from every note, grouped by topic. Click a question to reveal its answer.

<!-- Each note appends its section here: ## NN — Topic -->

## 01 — Spring Core: IoC & DI

_From [notes/01-core/spring-core.md](01-core/spring-core.md)_


<details><summary>Q1. What is IoC, and how is DI different from it?</summary>

IoC is the principle that a framework controls object creation and the flow of control, not your code. DI is one implementation of IoC: dependencies are *pushed* into an object (constructor, setter, or field) rather than *pulled* by it (`new`, a service locator, or JNDI). Spring's IoC container implements DI.
</details>

<details><summary>Q2. BeanFactory vs ApplicationContext?</summary>

`BeanFactory` is the core DI engine. It is lazy, and BPPs must be registered manually. `ApplicationContext` extends it with eager singleton creation, automatic BFPP/BPP detection, events, `Environment`/profiles, i18n (`MessageSource`), and resource loading. Apps always use `ApplicationContext`, which internally delegates to a `DefaultListableBeanFactory`.
</details>

<details><summary>Q3. Why is constructor injection preferred over field injection?</summary>

- Dependencies can be `final`, so the object is immutable and fully initialized on construction.
- Required dependencies are explicit.
- You can unit test with `new` and no container.
- Too many constructor parameters makes an SRP violation visible.
- Constructor circular dependencies fail fast.

Field injection hides dependencies, needs reflection, and allows partially constructed objects.
</details>

<details><summary>Q4. Walk through the bean lifecycle.</summary>

Instantiate (constructor injection) → field/setter injection → Aware callbacks (`BeanNameAware`, `BeanFactoryAware`...) → `BeanPostProcessor.postProcessBeforeInitialization` chain (`ApplicationContextAware` & co, then custom BPPs, then `@PostConstruct` via the internal `CommonAnnotationBeanPostProcessor`, which is re-registered last) → `InitializingBean.afterPropertiesSet` → custom init-method → `postProcessAfterInitialization` (AOP proxies are created here) → in use → on close: `@PreDestroy` → `DisposableBean.destroy` → custom destroy-method. Prototypes get no destruction callbacks.
</details>

<details><summary>Q5. BeanPostProcessor vs BeanFactoryPostProcessor?</summary>

A BFPP modifies bean *definitions* before any regular bean is instantiated. Examples are `ConfigurationClassPostProcessor` and `PropertySourcesPlaceholderConfigurer`. A BPP modifies bean *instances* around initialization. Examples are `@Autowired` processing and AOP proxy creation. Both should be declared as `static @Bean` methods.
</details>

<details><summary>Q6. How does Spring resolve circular dependencies, and what changed in Boot 2.6?</summary>

For field and setter injection on singletons, Spring exposes an early reference through a three-level cache (`singletonObjects`, `earlySingletonObjects`, `singletonFactories`). The third level lets the early reference be a proxy if needed. Constructor cycles can't be resolved (`BeanCurrentlyInCreationException`). Since Boot 2.6, circular references are prohibited by default (`spring.main.allow-circular-references=false`). Fix the design, or use `@Lazy` or `ObjectProvider`.
</details>

<details><summary>Q7. What happens when you inject a prototype bean into a singleton?</summary>

The prototype is created once, when the singleton is created, so it effectively becomes a singleton for that holder. To get a new instance each time, use `ObjectProvider<T>.getObject()`, a scoped proxy (`proxyMode = TARGET_CLASS`), or `@Lookup` method injection.
</details>

<details><summary>Q8. What's the difference between @Configuration and @Component when declaring @Bean methods?</summary>

A `@Configuration` (full mode) class is CGLIB-subclassed, so inter-`@Bean` method calls are intercepted and return the container's singleton. A `@Bean` inside a `@Component`, or `@Configuration(proxyBeanMethods = false)` (lite mode), is a plain method call that creates a new instance on each call. Boot's auto-configs use lite mode for faster startup and native-image friendliness, and they inject dependencies as method parameters.
</details>

<details><summary>Q9. Two beans implement the same interface. How does Spring choose?</summary>

It filters by `@Qualifier` if one is present. Then `@Primary` wins, then the highest `@Priority`, then the only non-`@Fallback` candidate, then a bean whose name matches the parameter or field name. Otherwise it throws `NoUniqueBeanDefinitionException`. Alternatively, inject `List<T>` or `Map<String, T>` to get all of them.
</details>

<details><summary>Q10. Are Spring singleton beans thread-safe?</summary>

No. Spring doesn't synchronize anything. A singleton is shared by all threads, so it must be stateless or use thread-safe structures. Request-specific state belongs in method locals, request-scoped beans, or the request itself.
</details>

<details><summary>Q11. Are application events synchronous? How do you make them run after commit?</summary>

They're synchronous by default and run on the publisher's thread, and an exception propagates to the publisher. Use `@Async` (with `@EnableAsync`) for async listeners. Use `@TransactionalEventListener` (default phase `AFTER_COMMIT`) to run after the transaction commits. Note that without a transaction, the event is dropped unless `fallbackExecution = true`.
</details>

<details><summary>Q12. Why might @Transactional not work when called from @PostConstruct?</summary>

`@PostConstruct` runs during initialization on the raw target object. The AOP proxy that applies transactions is created later, in `postProcessAfterInitialization`, and the call goes through `this` rather than the proxy anyway. Use `ApplicationReadyEvent`, `ApplicationRunner`, or `SmartInitializingSingleton` instead.
</details>

<details><summary>Q13. What is a FactoryBean, and how do you get the factory itself?</summary>

A `FactoryBean<T>` is a bean that produces another object. `getBean("x")` returns the product, and `getBean("&x")` returns the factory. It is used for complex construction, such as JPA's `LocalContainerEntityManagerFactoryBean`.
</details>

<details><summary>Q14. What's new in the Framework 7 core container?</summary>

- `BeanRegistrar` for programmatic bean registration.
- `@Proxyable` to choose the proxy type per bean.
- JSpecify null-safety annotations.
- Core resilience support: `@Retryable`, `@ConcurrencyLimit`, `@EnableResilientMethods`, and `RetryTemplate` in `org.springframework.core.retry`.
- Removal of `javax.annotation`/`javax.inject` support and of `spring-jcl`.
- SpEL `Optional` support and an operations limit.
- Baselines of JDK 17 and Jakarta EE 11.
</details>



## 00 — Java 21 refresher

_From [notes/00-java-refresher/java21-for-spring.md](00-java-refresher/java21-for-spring.md)_


<details><summary>Q1. What is a record, and why can't it be a JPA entity?</summary>

A record is a transparent, shallowly immutable data carrier. The compiler generates a `final` class with `private final` fields, a canonical constructor, accessors, and component-wise `equals`/`hashCode`/`toString`. A compact constructor validates and normalizes inputs.

JPA entities need:
- a no-arg constructor;
- a non-final class, so the provider can subclass it for lazy proxies;
- mutable fields, so it can set the generated id and version and do dirty checking;
- identity-based equality.

Records violate all four. Use records for DTOs, projections, `@ConfigurationProperties`, events, and (Hibernate 6.2+) `@Embeddable` value objects.
</details>

<details><summary>Q2. Why use a sealed interface with a pattern-matching switch instead of an instanceof chain or a visitor?</summary>

The compiler knows the full set of subtypes, so a `switch` without `default` is checked for **exhaustiveness**. Adding a variant breaks the compile of every `switch` that doesn't handle it, instead of silently falling through.

Record patterns deconstruct components inline (`case Approved(var tx, Money(var amt, var cur))`), and `when` guards refine a case. The visitor pattern achieves exhaustiveness with a lot of boilerplate, and `instanceof` chains have none.

Pitfalls:
- `default` defeats the check.
- Dominated cases must come after more specific (guarded) ones.
- An old compiled `switch` meeting a new subtype throws `MatchException`.
</details>

<details><summary>Q3. Stream.toList() vs Collectors.toList() vs Collectors.toUnmodifiableList()?</summary>

- `toList()` (Java 16+) returns an unmodifiable list that **allows nulls**.
- `Collectors.toList()` makes no guarantee about mutability, but in practice returns an `ArrayList`, and code often relies on mutating it.
- `toUnmodifiableList()` is unmodifiable and throws NPE on null elements.

When migrating, check whether the caller later mutates the list. If it does, use `toCollection(ArrayList::new)` to make that explicit.
</details>

<details><summary>Q4. When should you not use parallel streams?</summary>

Almost always in a web application.
- They run on the JVM-wide `ForkJoinPool.commonPool()`, so one slow request starves every other user of it, including `CompletableFuture.supplyAsync` without an executor.
- `ThreadLocal` context (transaction, security, MDC) isn't on the workers.
- Side effects on shared collections corrupt data. The demo lost 59% of the elements, or threw `ArrayIndexOutOfBoundsException`.
- Small or poorly splittable sources (`LinkedList`, I/O) are slower in parallel.

Use them only for large, CPU-bound, stateless work on splittable sources, and measure.
</details>

<details><summary>Q5. What are the do's and don'ts of Optional?</summary>

**Do:**
- Return `Optional` from lookups that may find nothing.
- Chain `map`/`filter`/`flatMap`.
- Use `orElseThrow` to turn absence into a domain exception.
- Use `orElseGet` for expensive defaults, because `orElse`'s argument is **always evaluated**.

**Don't:**
- Use it for fields, parameters, collections (return an empty collection), DTOs, or entities. It isn't serializable.
- Call `get()` without a check.
- Write `Optional.of(nullable)`, which throws NPE. Use `ofNullable`.

Framework 7 expresses nullability of parameters with JSpecify `@Nullable` instead.
</details>

<details><summary>Q6. State the equals/hashCode contract. How do you implement it for a JPA entity?</summary>

`equals` must be reflexive, symmetric, transitive, consistent, and false for `null`. Equal objects must have equal hash codes, and the hash code must not change while the object is in a hash-based collection.

For an entity, the generated id is null before persist and then assigned. So:
- `equals` is true only if `this == o`, or both are the same entity type (via `instanceof`, because Hibernate proxies are subclasses) with the **same non-null id**, read through `getId()` so it works on a lazy proxy;
- `hashCode` returns a **constant** per entity class (`Order.class.hashCode()`). Don't use `getClass()`, which returns the proxy class for a proxy.

The alternative is an immutable natural business key. Never use Lombok `@Data`: its all-field `equals`/`hashCode` touches lazy associations and recurses.
</details>

<details><summary>Q7. Checked vs unchecked exceptions: why does Spring prefer unchecked, and how does that affect @Transactional?</summary>

Checked exceptions force handling at compile time. That suits recoverable, expected conditions. Most infrastructure failures can't be handled where they occur, and checked `SQLException` led to swallowed or boilerplate catches. Spring translates vendor exceptions into the unchecked, portable `DataAccessException` hierarchy (via `SQLExceptionTranslator` and `PersistenceExceptionTranslationPostProcessor` for `@Repository`). Unchecked exceptions also work in lambdas.

By default `@Transactional` rolls back on `RuntimeException` and `Error` only. A checked exception **commits**. Override with `rollbackFor`, or globally with `@EnableTransactionManagement(rollbackOn = RollbackOn.ALL_EXCEPTIONS)` (Framework 6.2+).
</details>

<details><summary>Q8. What is type erasure, and how does Spring still know you want a List of OrderDto?</summary>

Generic type arguments are removed from runtime objects: `ArrayList<String>` and `ArrayList<Integer>` share one `Class`. But generic **declarations** (superclass, interfaces, field, parameter, and return types) are kept in the class file's `Signature` attribute and exposed via `ParameterizedType`.

Spring's `ResolvableType` reads them for generic injection and event matching. For a call site like `body(...)`, you pass `new ParameterizedTypeReference<List<OrderDto>>() {}`. The `{}` creates an anonymous subclass, whose `getGenericSuperclass()` carries `List<OrderDto>`. This is the "super type token" pattern, the same one as Jackson's `TypeReference`.
</details>

<details><summary>Q9. Explain PECS with an example.</summary>

"Producer Extends, Consumer Super."
- If a parameter **produces** values you read, use `? extends T`: `double sum(List<? extends Number>)` accepts `List<Integer>` and `List<Double>`, but you can't add to it.
- If it **consumes** values you write, use `? super T`: `void fill(List<? super Integer>)` accepts `List<Number>` and `List<Object>`.

`Collections.copy(List<? super T> dest, List<? extends T> src)` uses both. It's needed because generics are invariant: `List<Integer>` isn't a `List<Number>`.
</details>

<details><summary>Q10. How does Spring use ThreadLocal, and what goes wrong with @Async?</summary>

Spring uses `ThreadLocal` in several places:
- `TransactionSynchronizationManager` binds the transaction's connection or `EntityManager` to the thread, so repositories join the service's transaction.
- `SecurityContextHolder` holds the `Authentication`.
- `RequestContextHolder` holds the current request.
- `MDC` holds log context.

An `@Async` method, a `CompletableFuture`, or a parallel stream runs on another thread, so it sees **none** of this. It gets no transaction (it starts its own if annotated), no authentication, and no traceId. Fix this with a `TaskDecorator` or `DelegatingSecurityContextAsyncTaskExecutor`, which capture on the caller and restore on the worker.

Pooled threads also make leaks possible: always `remove()` in `finally`. `ScopedValue` (preview in 21, final in 25) fixes this by design.
</details>

<details><summary>Q11. What are virtual threads, when do they help, and what is pinning?</summary>

Virtual threads are JDK-scheduled `Thread`s (JEP 444, final in 21) whose stacks live on the heap. They mount on a small `ForkJoinPool` of carrier threads, and when they block on I/O, they unmount, freeing the carrier. This makes thread-per-request code scale to many thousands of concurrent **blocking I/O** calls. They don't speed up CPU-bound work, and they don't remove other limits like DB connection pools.

**Pinning** means a virtual thread can't unmount, so it blocks its carrier. On JDK 21 that happens when it blocks inside `synchronized` (including `Object.wait`) or a native frame. JEP 491 (JDK 24) removed the `synchronized` case. Detect pinning with JFR `jdk.VirtualThreadPinned`, or `-Djdk.tracePinnedThreads` on 21–23.

Rules: never pool virtual threads, avoid `ThreadLocal` caches, and throttle with a semaphore, not with a pool.
</details>

<details><summary>Q12. What does spring.threads.virtual.enabled=true change in Spring Boot?</summary>

On Java 21+:
- The embedded server handles requests on virtual threads.
- The auto-configured `applicationTaskExecutor` (`@Async`) becomes a `SimpleAsyncTaskExecutor` using virtual threads.
- The scheduler becomes a `SimpleAsyncTaskScheduler` using virtual threads.

Thread-pool properties stop having any effect. Because virtual threads are daemon threads, an app kept alive only by `@Scheduled` work can exit, so set `spring.main.keep-alive=true`. The Boot reference strongly recommends Java 24+ because of pinning.
</details>

<details><summary>Q13. thenApply vs thenCompose, and how do you handle errors and timeouts in CompletableFuture?</summary>

- `thenApply` maps the value (`T -> R`).
- `thenCompose` flat-maps when your function itself returns a `CompletableFuture`, which avoids nested futures.
- `thenCombine` joins two independent futures.
- `allOf` waits for many.

For errors, `exceptionally` recovers, `handle` sees both value and exception, and `whenComplete` observes. `join()` throws `CompletionException` and `get()` throws `ExecutionException`, both wrapping the cause.

For timeouts, `orTimeout` fails the future and `completeOnTimeout` substitutes a value. Without an executor argument, async stages run on `ForkJoinPool.commonPool()`. Pass your own executor (or a virtual-thread executor) for I/O.
</details>

<details><summary>Q14. JDK dynamic proxy vs CGLIB, and why does self-invocation bypass @Transactional?</summary>

- A JDK proxy (`java.lang.reflect.Proxy`) implements the target's **interfaces** and dispatches to an `InvocationHandler`. It isn't an instance of the target class.
- CGLIB generates a **subclass**, so it can't intercept `final` or `private` methods or `final` classes. Boot defaults to CGLIB, and Framework 7 applies that default to all proxy processors, including `@Async`.

In both cases, advice runs only when the call goes **through the proxy**. A method calling `this.other()` inside the target calls the raw object, so the transaction, cache, or security advice never runs. Fixes:
- move the method to another bean;
- inject the bean's own proxy (`@Lazy` self-injection or `ObjectProvider`);
- use AspectJ weaving.
</details>

<details><summary>Q15. volatile vs AtomicInteger vs synchronized, and why ConcurrentHashMap over a synchronized HashMap?</summary>

- `volatile` guarantees visibility and ordering only. `count++` on it still races.
- `AtomicInteger` (CAS) makes single-variable read-modify-write atomic and lock-free. `LongAdder` is better for hot counters.
- `synchronized` / `ReentrantLock` give mutual exclusion over multi-step invariants.

In the demo, a racy `int` reached 667,406 of 800,000, and `AtomicInteger` reached exactly 800,000.

`ConcurrentHashMap` has lock-free reads and per-bin write locking, so it scales far better than one global lock. Its `compute`, `computeIfAbsent`, and `merge` are atomic per key, while `get`-then-`put` isn't. It rejects nulls, so a `null` from `get` always means "absent".
</details>



## 02 — AOP: proxies & aspects

_From [notes/02-aop/spring-aop.md](02-aop/spring-aop.md)_


<details><summary>Q1. Define aspect, join point, pointcut, advice, advisor, target, proxy and weaving.</summary>

- An **aspect** is a module bundling a cross-cutting concern.
- A **join point** is a point in execution where advice can apply. In Spring AOP that's always a method execution on a bean.
- A **pointcut** is an expression selecting join points.
- **Advice** is the code run at a join point (`@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`).
- An **advisor** is Spring's pairing of exactly one pointcut with one advice.
- The **target** is the real object.
- The **proxy** is the object the container hands out, which runs the advice chain and then calls the target.
- **Weaving** links aspects to targets. Spring does it at runtime by proxying, while AspectJ does it at compile time or load time by rewriting bytecode.
</details>

<details><summary>Q2. Spring AOP vs AspectJ: what are the differences, and when would you need AspectJ?</summary>

Spring AOP is proxy-based and runtime-only. It supports only method-execution join points on Spring beans, can't advise self-invocation or `private`/`final`/`static` methods, and costs a proxy dispatch per call. AspectJ weaves bytecode (compile-time with `ajc`, or load-time with the `aspectjweaver` agent). It supports field access, constructors, call-site join points and any object (including `new`), and self-calls are advised. Spring AOP uses AspectJ's annotations and pointcut parser but not its weaver. Choose AspectJ when you need to advise non-beans or self-calls, or need maximum performance, for example `@Transactional(mode = ASPECTJ)`.
</details>

<details><summary>Q3. JDK dynamic proxy vs CGLIB. Which does Spring Boot use, and what changed in Framework 7?</summary>

A JDK proxy implements the bean's interfaces, so it isn't an instance of the class and only interface methods are advised. CGLIB generates a subclass, so it can be injected by class, but it can't handle `final` classes or methods. Plain Framework defaults to JDK proxies when interfaces exist. **Boot defaults to CGLIB** (`spring.aop.proxy-target-class=true`). Framework 7 applies the global default **consistently to every proxy processor** (`@Async`, `@Retryable`, …) and adds **`@Proxyable`** to override it per bean (`INTERFACES`, `TARGET_CLASS`, or specific `interfaces`).
</details>

<details><summary>Q4. Why doesn't @Transactional work when I call the method from another method in the same class?</summary>

Because of self-invocation. The proxy only intercepts calls coming from outside. Inside the target, `this.method()` is a plain Java call on the raw object, so no advice runs. Fixes, in order of preference:
1. Move the method to another bean.
2. Call through a self-reference obtained lazily (`ObjectProvider<Self>` or `@Lazy Self`, since a plain self-injection is a forbidden circular reference in Boot).
3. `AopContext.currentProxy()` with `exposeProxy = true` (discouraged).
4. AspectJ weaving.
</details>

<details><summary>Q5. In what order do advice types run within a single aspect, and why does @After run after @AfterReturning?</summary>

The precedence, from highest (outermost) to lowest, is `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. On success the recorded order is around-before, before, target, afterReturning, after, around-after. On failure it's around-before, before, target, afterThrowing, after, around-catch. `@After` has "after finally" semantics: it's a `try/finally` interceptor that *wraps* the `AfterReturning`/`AfterThrowing` interceptors, so its `finally` runs after them.
</details>

<details><summary>Q6. Two aspects advise the same method. How do you control which runs first? What if two @Before methods are in the same aspect?</summary>

Put `@Order` on the aspect class (or implement `Ordered`). A lower value means higher precedence, so that aspect runs first on the way in and last on the way out. Without it, the order is undefined. `@Order` on advice methods is ignored. Two advices of the same type in one aspect have an **undefined** order, because reflection can't recover declaration order from javac-compiled classes. Merge them, or move them into separately ordered aspects.
</details>

<details><summary>Q7. Explain this() vs target() in Spring AOP pointcuts.</summary>

`this(T)` matches when the **proxy** is an instance of `T`, while `target(T)` matches when the **target object** is. With CGLIB, the proxy is a subclass, so both match the implementation class. With a JDK proxy, the proxy only implements interfaces, so `this(ImplClass)` fails and `target(ImplClass)` matches. In native AspectJ they refer to the same object at an execution join point. The distinction exists only because Spring AOP is proxy-based.
</details>

<details><summary>Q8. Where in the bean lifecycle are AOP proxies created, and by which class?</summary>

In `BeanPostProcessor.postProcessAfterInitialization`, after `@PostConstruct` and init methods. `AbstractAutoProxyCreator.wrapIfNecessary` finds eligible advisors (in Boot, `AnnotationAwareAspectJAutoProxyCreator` turns `@Aspect` methods into advisors and also collects `Advisor` beans for tx, cache and security), sorts them, and builds the proxy with `ProxyFactory`. If the bean is part of a circular reference, the proxy is created earlier in `getEarlyBeanReference`. Some features (`@Async`, `@Retryable`, `@ConcurrencyLimit`, method validation) use `AbstractAdvisingBeanPostProcessor` instead, which adds one advisor to an existing proxy or creates a new one.
</details>

<details><summary>Q9. Why can't final or private methods be advised? What actually happens if you call a final method on a CGLIB proxy?</summary>

CGLIB works by overriding methods in a subclass, and `final` and `private` methods can't be overridden. A `private` method is only reachable from inside the class anyway (self-invocation). A `final` method call executes **on the proxy instance itself**, which Objenesis created without running the constructor, so its fields are `null`. You get no advice and possibly an NPE on "injected" fields. Spring logs a warning at startup.
</details>

<details><summary>Q10. You have @Transactional, @Cacheable and a custom logging aspect on one method. In what order do they run, and how do you control it?</summary>

All three are advisors in the same auto-proxy creator chain, sorted by order. `@EnableTransactionManagement` and `@EnableCaching` default to `LOWEST_PRECEDENCE`, as does an `@Aspect` without `@Order`, so ties are not something to rely on. Set the orders explicitly: `@EnableCaching(order = ..)`, `@EnableTransactionManagement(order = ..)` and `@Order` on the aspect. A good layout is security (Spring Security's interceptors default to small values, e.g. 200 for `@PreAuthorize`), then caching (a cache hit skips the transaction entirely), then logging/audit, then the transaction, then the target. If `@Retryable` is added, it's always outside tx and cache. In the lab it's also outside the custom aspect, because the retry post-processor inserts its advisor at index 0 of the existing proxy.
</details>

<details><summary>Q11. What are @Retryable and @ConcurrencyLimit in Spring Framework 7, and how do they differ from Spring Retry?</summary>

They're core annotations in `org.springframework.resilience.annotation`, enabled with `@EnableResilientMethods`, which Boot doesn't do automatically.
- **`@Retryable`** has these attributes:
  - `includes`/`excludes` (matched against the exception and its causes) and `predicate`;
  - `maxRetries` (default 3, *after* the first call);
  - `delay` (default 1s), `multiplier`, `jitter` and `maxDelay`;
  - `timeout` (7.0.2+), `timeUnit`, and `*String` variants for placeholders/SpEL.

  On exhaustion, the original exception propagates. It also supports reactive return types.
- **`@ConcurrencyLimit(n)`** throttles concurrent calls. The `policy` is `BLOCK` (default) or `REJECT`, which throws `InvocationRejectedException` (7.0.3+).
- The programmatic API is `RetryTemplate`/`RetryPolicy` in `org.springframework.core.retry`.

Compared with Spring Retry: there's no `@Recover`, `maxRetries` replaces `maxAttempts` (total), the packages differ, and Boot 4 dropped dependency management for `spring-retry`.
</details>

<details><summary>Q12. When should you use a retry and when a circuit breaker?</summary>

Retry handles **transient** failures where the next attempt is likely to succeed. It's stateless and multiplies load. A circuit breaker handles **sustained** failures. It's stateful, tracking the failure rate, and it opens to fail fast and give the downstream time to recover, usually with a fallback. Production systems combine them: circuit breaker outermost, then a bulkhead or concurrency limit, then retry with back-off and jitter, then the call. Only retry idempotent operations, and retry at one layer only to avoid multiplicative retry storms.
</details>

<details><summary>Q13. How do you write an annotation-driven aspect, and what are the common mistakes?</summary>

1. Declare an annotation with `@Retention(RUNTIME)`.
2. Write an `@Aspect @Component` class with `@Around("within(com.acme..*) && @annotation(myAnno)")` and a parameter `MyAnno myAnno` to bind it.
3. Call `pjp.proceed()` and return its result.

Common mistakes:
- forgetting `@Component`;
- `CLASS` retention;
- not returning the result;
- swallowing exceptions;
- putting `@Order` on the method instead of the class;
- leaving the pointcut unscoped;
- relying on it for self-calls.
</details>

<details><summary>Q14. What does exposeProxy do, and why is AopContext.currentProxy() discouraged?</summary>

`exposeProxy = true` makes the proxy store itself in a `ThreadLocal` (`AopContext`) for the duration of each call, so the target can fetch it and call itself through the proxy. It's discouraged because it couples business code to Spring AOP, adds a `ThreadLocal` cost to every call, and fails on other threads. Restructuring into two beans is cleaner.
</details>

<details><summary>Q15. Is Spring AOP slow? What does a proxied call cost?</summary>

A proxied call walks the interceptor list held by `ReflectiveMethodInvocation` and invokes the target reflectively (CGLIB uses a `MethodProxy`). The chain per method is computed once and cached, so the per-call cost is small (sub-microsecond for a few interceptors) and negligible next to I/O. It matters only in hot loops, or with dynamic pointcuts (`args`, `this`, `target`) that need a runtime check per call. If it's measurable, move the hot path out of the proxied bean or use AspectJ compile-time weaving.
</details>



## 03 — Boot internals: auto-configuration

_From [notes/03-boot-internals/auto-configuration.md](03-boot-internals/auto-configuration.md)_


<details><summary>Q1. What does @SpringBootApplication do?</summary>

It is a composed annotation with three parts:
- `@SpringBootConfiguration`, a `@Configuration` that test slices look up to find the app.
- `@EnableAutoConfiguration`, which imports `AutoConfigurationImportSelector` and registers the base package through `@AutoConfigurationPackage` (used by entity scanning and Spring Data).
- `@ComponentScan` from the class's package, with `TypeExcludeFilter` and `AutoConfigurationExcludeFilter`.

Its attributes `exclude`, `excludeName`, `scanBasePackages` and `proxyBeanMethods` are aliases for those parts.
</details>

<details><summary>Q2. How does Spring Boot find auto-configuration classes?</summary>

`AutoConfigurationImportSelector` calls `ImportCandidates.load(AutoConfiguration.class, classLoader)`, which reads every `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` on the classpath. Then it:
1. removes duplicates and exclusions (`exclude`, `spring.autoconfigure.exclude`);
2. runs a fast filter (`OnClassCondition`, `OnBeanCondition`, `OnWebApplicationCondition`) driven by the precomputed `spring-autoconfigure-metadata.properties`;
3. sorts the rest (`@AutoConfigureOrder`, `before`/`after`);
4. hands them to the configuration-class parser for full condition evaluation.

`spring.factories` stopped being used for this in Boot 3.0.
</details>

<details><summary>Q3. Why does a user-defined bean always win over an auto-configured one?</summary>

`AutoConfigurationImportSelector` is a `DeferredImportSelector`, so auto-configurations are parsed only after all user configuration and component-scanned classes. When an auto-config's `@ConditionalOnMissingBean` is evaluated (in the `REGISTER_BEAN` phase), the user's bean definition already exists, so the auto-config backs off. The flip side: `@ConditionalOnBean` in user config can't see auto-configured beans.
</details>

<details><summary>Q4. List the main @Conditional annotations Boot provides.</summary>

- **Classpath:** `@ConditionalOnClass` / `OnMissingClass`.
- **Beans:** `@ConditionalOnBean` / `OnMissingBean` / `OnSingleCandidate` / `OnMissingFilterBean`.
- **Properties:** `@ConditionalOnProperty` / `OnBooleanProperty`.
- **Environment:** `@ConditionalOnResource`, `@ConditionalOnWebApplication` / `OnNotWebApplication`, `@ConditionalOnWarDeployment` / `OnNotWarDeployment`, `@ConditionalOnCloudPlatform`, `@ConditionalOnJava`, `@ConditionalOnJndi`, `@ConditionalOnThreading`, `@ConditionalOnCheckpointRestore`.
- **Escape hatch:** `@ConditionalOnExpression`.

Spring's `@Profile` is also a condition.
</details>

<details><summary>Q5. What is the difference between @AutoConfigureOrder and @AutoConfiguration(after = ...)?</summary>

`after`/`before` express a relative dependency between two specific auto-configs. Use them when one's conditions need the other's beans. `@AutoConfigureOrder` is an absolute priority, like `@Order` but only among auto-configs, for classes that shouldn't know each other. Both affect only bean-definition registration order, not bean creation order.
</details>

<details><summary>Q6. How do you find out why an auto-configuration did or didn't apply?</summary>

- Start with `--debug` (or `debug=true`) to print the Conditions Evaluation Report: positive matches, negative matches with the failing condition's message, exclusions, and unconditional classes.
- In a running app, use `/actuator/conditions`.
- In tests, use `ConditionEvaluationReport.get(beanFactory)` or `ConditionEvaluationReportLoggingListener` on an `ApplicationContextRunner`.
</details>

<details><summary>Q7. How would you build a custom starter?</summary>

- **Auto-configure module:** your `@AutoConfiguration` classes with `@ConditionalOnClass`, `@ConditionalOnMissingBean` and property conditions, and an `@ConfigurationProperties` record under your own prefix (validated). List the classes in `META-INF/spring/...AutoConfiguration.imports`, make library dependencies `optional`, and add `spring-boot-configuration-processor` and `spring-boot-autoconfigure-processor`.
- **Starter module:** a pom-only artifact depending on the auto-configure module, `spring-boot-starter`, and the libraries.
- **Naming:** `acme-spring-boot-starter`, never `spring-boot-...`.
- **Tests:** `ApplicationContextRunner` for each condition, `FilteredClassLoader` for missing classes, and `withUserConfiguration` to prove back-off.
</details>

<details><summary>Q8. Walk through SpringApplication.run().</summary>

1. Create the bootstrap context (run `BootstrapRegistryInitializer`s), then `ApplicationStartingEvent`.
2. Prepare the `Environment`: command-line args, then `ApplicationEnvironmentPreparedEvent`, during which `EnvironmentPostProcessor`s run and `application.yml` and profiles are loaded. Bind `spring.main.*`.
3. Print the banner and create the context for the `WebApplicationType`.
4. Apply `ApplicationContextInitializer`s, then `ApplicationContextInitializedEvent`. Close the bootstrap context and load sources, then `ApplicationPreparedEvent`.
5. `refresh()`: auto-config is parsed and the web server is created in `onRefresh` and started in `finishRefresh`. Then `ContextRefreshedEvent`.
6. `ApplicationStartedEvent` + liveness `CORRECT`.
7. Runners.
8. `ApplicationReadyEvent` + readiness `ACCEPTING_TRAFFIC`.

On failure: `ApplicationFailedEvent` and the failure analyzers.
</details>

<details><summary>Q9. What changed with starters in Spring Boot 4?</summary>

Boot 4 is modular: `spring-boot-<tech>` modules each contain their own auto-configurations and `.imports` file, so a technology's auto-config isn't even on the classpath without its starter. Several starters were renamed:
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`;
- `spring-boot-starter-aop` → `spring-boot-starter-aspectj`;
- the OAuth2 starters → `spring-boot-starter-security-oauth2-*`.

Test support split into `spring-boot-starter-<tech>-test`. `spring-boot-starter-classic` and `spring-boot-starter-test-classic` exist to ease migration.
</details>

<details><summary>Q10. How does an executable Spring Boot jar work?</summary>

The manifest's `Main-Class` is `org.springframework.boot.loader.launch.JarLauncher`, and `Start-Class` is your app. App classes are in `BOOT-INF/classes` and dependencies are stored unextracted in `BOOT-INF/lib`. The launcher creates a class loader that reads nested jars in place (`jar:nested:` URLs) in `classpath.idx` order, then invokes your `main`. `layers.idx` drives Docker layering. Boot 4 removed the classic loader and the launch script, and no longer packages optional dependencies by default.
</details>

<details><summary>Q11. When would you use an EnvironmentPostProcessor vs an ApplicationContextInitializer vs a BootstrapRegistryInitializer?</summary>

- **`BootstrapRegistryInitializer`:** register objects needed *while building configuration*, such as a Vault client. It runs first.
- **`EnvironmentPostProcessor`:** add, modify or decrypt property sources before the context exists, such as platform defaults or secrets. Register it in `spring.factories`; in Boot 4 the key is `org.springframework.boot.EnvironmentPostProcessor`.
- **`ApplicationContextInitializer`:** customize the context after it's created and before beans are defined, such as registering singletons or setting active profiles programmatically.
</details>

<details><summary>Q12. Where and when does the embedded Tomcat start?</summary>

`ServletWebServerApplicationContext.onRefresh()` creates it through the auto-configured `ServletWebServerFactory`, which registers `DispatcherServlet` and filter beans into the `ServletContext`. The connector only begins accepting requests when `WebServerStartStopLifecycle` (a `SmartLifecycle`) starts it in `finishRefresh()`, after all singletons are created. `ServletWebServerInitializedEvent` then carries the port.
</details>

<details><summary>Q13. What is a FailureAnalyzer?</summary>

It's an extension that converts a startup exception into a human-readable "Description / Action" report. Boot ships many, for example for bind validation, port in use, a missing bean, or a bean override. You write one by extending `AbstractFailureAnalyzer<T extends Throwable>` and registering it in `spring.factories` under `org.springframework.boot.diagnostics.FailureAnalyzer`. `FailureAnalyzers` is the `SpringBootExceptionReporter` that runs them.
</details>

<details><summary>Q14. Why do Boot's auto-configs use proxyBeanMethods = false?</summary>

Lite mode avoids CGLIB subclassing of hundreds of configuration classes, which makes startup faster and is friendlier to AOT and native images. Auto-configs never call other `@Bean` methods directly. They take dependencies as method parameters. `@AutoConfiguration` sets `proxyBeanMethods = false` for you.
</details>



## 04 — Configuration & profiles

_From [notes/04-configuration/externalized-config.md](04-configuration/externalized-config.md)_


<details><summary>Q1. What is the property-source precedence in Spring Boot?</summary>

From lowest to highest:
1. default properties;
2. `@PropertySource`;
3. config data (`application.yml` and profile files, with profile-specific and outside-jar files winning);
4. `random.*`;
5. OS env vars;
6. Java system properties;
7. JNDI;
8. `ServletContext` params;
9. `ServletConfig` params;
10. `SPRING_APPLICATION_JSON`;
11. command-line args;
12. `@SpringBootTest(properties)`;
13. `@DynamicPropertySource`;
14. `@TestPropertySource`;
15. devtools global settings.

The rule of thumb: the closer to the launch, the higher the priority.
</details>

<details><summary>Q2. How do profile-specific files and multi-document YAML interact?</summary>

`application-{profile}.yml` files load after `application.yml` and override it. With several active profiles, the last one wins. In a multi-document file, `---` (or `#---` in properties) separates documents, and `spring.config.activate.on-profile` activates a document. Later documents override earlier ones. `spring.profiles.active`/`include`/`group` can't appear in profile-specific files or activated documents.
</details>

<details><summary>Q3. @ConfigurationProperties vs @Value?</summary>

`@ConfigurationProperties` binds a whole prefix into a typed object (ideally an immutable record). It supports relaxed binding, rich conversion (`Duration`, `DataSize`, collections), JSR-380 validation with `@Validated`, and IDE metadata. `@Value` injects a single key and supports SpEL, but has no validation or metadata, and is relaxed only when you write the canonical kebab-case key. Use `@ConfigurationProperties` for anything grouped or reused, and `@Value` for one-offs or SpEL.
</details>

<details><summary>Q4. Explain relaxed binding and the environment variable rules.</summary>

`my.main-project.first-name`, `my.mainProject.firstName`, `my.main_project.first_name`, and `MY_MAINPROJECT_FIRSTNAME` all bind to the same property. To build the env var form, replace `.` with `_`, remove `-`, and uppercase. List indexes become `_0_` (`MY_LIST_0_NAME`). Env-var map keys are lowercased. Map keys with special characters need `[...]`. Always use the canonical kebab-case form in files, `@Value`, and placeholders.
</details>

<details><summary>Q5. What does spring.config.import do? What do optional: and configtree: mean?</summary>

It pulls additional config data into the `Environment`, inserted just above the importing document, so it overrides that document. `optional:` means a missing location doesn't fail startup. `configtree:` imports a directory tree where each file path is a key and the file content is the value, as Kubernetes mounts ConfigMaps and Secrets. Other prefixes come from libraries: `configserver:`, `aws-secretsmanager:`, `vault://`, and Boot's own `env:` for a whole document in one env var.
</details>

<details><summary>Q6. How do you activate profiles, and what are profile groups?</summary>

Use `spring.profiles.active` (property, env var `SPRING_PROFILES_ACTIVE`, or `--spring.profiles.active`), `SpringApplication.setAdditionalProfiles`, or `@ActiveProfiles` in tests. `spring.profiles.include` adds profiles on top. `spring.profiles.group.prod=proddb,prodmq` makes `prod` expand to its members. With no active profile, `default` is used (configurable with `spring.profiles.default`).
</details>

<details><summary>Q7. What does @Profile("prod & (eu | us)") mean?</summary>

The bean or config class is registered only if `prod` is active **and** either `eu` or `us` is active. Profile expressions support `!`, `&`, and `|`, and mixing `&` and `|` requires parentheses. `@Profile({"a","b"})` means a OR b. `@Profile` is implemented as a `@Conditional` (`ProfileCondition`).
</details>

<details><summary>Q8. How do you validate configuration at startup?</summary>

Annotate the `@ConfigurationProperties` type with `@Validated`, put Bean Validation constraints on the fields or record components, put `@Valid` on nested objects, and make sure a validator (`spring-boot-starter-validation`) is on the classpath. An invalid value fails startup with `BindValidationException`, and `BindValidationFailureAnalyzer` prints the property, value, origin, and reason.
</details>

<details><summary>Q9. What's the difference between ${...} and #{...}?</summary>

`${key:default}` is a property placeholder resolved from the `Environment` by the placeholder configurer, or by the `Environment` itself in config files. `#{...}` is a SpEL expression evaluated when the bean is created, and it can call methods, reference beans, and use operators. They compose: placeholders resolve first, then SpEL runs (`#{'${app.owner}'.toUpperCase()}`). Since Framework 6.2, `\${...}` escapes a placeholder.
</details>

<details><summary>Q10. How do you handle secrets in Spring Boot?</summary>

Keep them out of git and out of the image:
- inject them at runtime as env vars;
- better, as mounted files imported with `configtree:`;
- or import them from a secrets manager (`aws-secretsmanager:`, Vault, Spring Cloud Config with `{cipher}`).

Boot itself doesn't encrypt properties. Keep actuator's value masking on, don't log config objects that contain secrets, and rotate credentials with a restart or refresh strategy.
</details>

<details><summary>Q11. How does Spring Boot work with Kubernetes ConfigMaps?</summary>

There are two ways:
- **`envFrom`/`env`:** keys become env vars and bind through relaxed binding.
- **Volume mounts:** imported with `spring.config.import=optional:configtree:/etc/config/` (or the `*/` wildcard for several mounts). Each file becomes a property.

Boot reads config at startup, so changes need a rolling restart unless you add Spring Cloud Kubernetes reload. `spring.config.activate.on-cloud-platform: kubernetes` and `@ConditionalOnCloudPlatform` allow cluster-specific config.
</details>

<details><summary>Q12. What is twelve-factor config, and how does Boot support it?</summary>

Factor III says config that varies between deploys belongs in the environment, strictly separated from code, so one build artifact is promoted through all environments. Boot supports it with env-var relaxed binding, the precedence order (environment overrides packaged defaults), `spring.config.import` for mounted files and secret stores, and fail-fast validation. Profiles should express capabilities, not hard-coded environment logic.
</details>

<details><summary>Q13. How are lists and maps merged across property sources?</summary>

Lists are replaced wholesale: the highest-priority source that defines the list wins, and the items are never merged. Maps are merged by key, and each key takes its value from the highest-priority source defining it. This applies across all sources, not just files.
</details>

<details><summary>Q14. Why do Duration properties beat long timeoutMs properties?</summary>

A `Duration` carries its unit in the value (`30s`, `5m`, `PT1H`), so the property name doesn't need a unit suffix, and no one misreads seconds as milliseconds. Boot converts it automatically. A bare number defaults to milliseconds unless `@DurationUnit` says otherwise, and IDE metadata documents the type. The same goes for `DataSize` (`10MB`) and `Period`.
</details>



## 07 — Spring Security 7

_From [notes/07-security/spring-security.md](07-security/spring-security.md)_


<details><summary>Q1. Walk through what happens when a request hits a Spring Security protected endpoint.</summary>

Tomcat calls `DelegatingFilterProxy` (registered by Boot as `springSecurityFilterChain`, order -100), which delegates to the `FilterChainProxy` bean. `FilterChainProxy` runs the `HttpFirewall`, then picks the **first** `SecurityFilterChain` whose `securityMatcher` matches, and runs its filters in order:
1. `SecurityContextHolderFilter` lazily loads the context.
2. Headers and CORS run.
3. An authentication filter (`BearerTokenAuthenticationFilter`, `UsernamePasswordAuthenticationFilter`, `BasicAuthenticationFilter`) turns credentials into an `Authentication` via the `AuthenticationManager` and stores it in `SecurityContextHolder`.
4. `AnonymousAuthenticationFilter` fills in an anonymous token if nothing authenticated the request.
5. `ExceptionTranslationFilter` wraps the rest.
6. `AuthorizationFilter` evaluates the `authorizeHttpRequests` rules.

If the request is allowed, it reaches `DispatcherServlet`, where method-security proxies may check again. Afterwards `FilterChainProxy` clears the context.
</details>

<details><summary>Q2. Why does Spring Security need DelegatingFilterProxy AND FilterChainProxy?</summary>

The servlet container creates filters before the Spring `ApplicationContext` exists and knows nothing about beans. `DelegatingFilterProxy` is a plain servlet filter that lazily looks up a bean by name, which bridges the two lifecycles. `FilterChainProxy` is that bean. It gives Spring Security a single entry point that can:
- hold *several* chains with request matchers;
- apply the firewall;
- always clear the `SecurityContext`;
- serve as one place to debug.

All without registering dozens of filters in the container.
</details>

<details><summary>Q3. Authentication vs authorization, and 401 vs 403. Who decides which is returned?</summary>

Authentication establishes identity, and authorization decides access. `ExceptionTranslationFilter` decides. An `AuthenticationException`, or an `AccessDeniedException` for an **anonymous** (or remember-me) user, triggers the `AuthenticationEntryPoint`, which returns 401 with `WWW-Authenticate`, or a redirect to login. An `AccessDeniedException` for an authenticated user goes to the `AccessDeniedHandler`, which returns 403. A bad bearer token fails earlier, in `BearerTokenAuthenticationFilter`, which calls the resource server's entry point directly.
</details>

<details><summary>Q4. Explain AuthenticationManager, ProviderManager, AuthenticationProvider, UserDetailsService and PasswordEncoder.</summary>

- **`AuthenticationManager`** is the single-method contract `authenticate(Authentication)`.
- **`ProviderManager`** implements it by iterating `AuthenticationProvider`s that `supports()` the token type, falling back to a parent manager, erasing credentials, and publishing events.
- **`DaoAuthenticationProvider`** loads a `UserDetails` from a `UserDetailsService` and runs account checks (locked, disabled, expired). It verifies the password with a `PasswordEncoder`, optionally upgrades the hash via `UserDetailsPasswordService`, and hides user-not-found as bad credentials, with a dummy hash check against timing attacks.
- **`JwtAuthenticationProvider`** is the bearer equivalent: `JwtDecoder` plus `JwtAuthenticationConverter`.
</details>

<details><summary>Q5. Why is the SecurityContext empty inside an @Async method, and how do you fix it?</summary>

The default `SecurityContextHolderStrategy` is a `ThreadLocal`, and `@Async` runs the method on an executor thread that never had the context set. Fixes:
- Wrap the executor in `DelegatingSecurityContextAsyncTaskExecutor` (or the `ExecutorService`/`Runnable` variants), which copies the context in and clears it afterwards.
- Use Micrometer context propagation.
- Pass the needed identity as parameters.

Avoid `MODE_INHERITABLETHREADLOCAL` with thread pools, because pooled threads keep the context of whoever created them.
</details>

<details><summary>Q6. How do you implement JWT authentication in modern Spring, and why not write a JwtFilter?</summary>

Use the OAuth2 resource server: `http.oauth2ResourceServer(o -> o.jwt(...))` with a `JwtDecoder`. Either `issuer-uri` (JWKS discovery for an IdP) or `NimbusJwtDecoder.withPublicKey`/`withSecretKey` for self-issued tokens, with `NimbusJwtEncoder` to sign them. Map claims with a `JwtAuthenticationConverter`. The framework handles:
- header parsing (RFC 6750);
- signature verification with algorithm pinning;
- `exp`/`nbf` with clock skew, `typ`, and optional `iss`/`aud` validators;
- JWKS caching and key rotation;
- correct 401 + `WWW-Authenticate` responses;
- integration with `ExceptionTranslationFilter`.

Hand-rolled filters routinely miss several of these and break `permitAll` paths.
</details>

<details><summary>Q7. RS256 vs HS256, and what is a JWK set?</summary>

HS256 is an HMAC with one shared secret. Everyone who can verify can also forge, so it only suits a single app. RS256/ES256 sign with a private key and verify with a public key, so many services can verify and only the issuer can mint. A JWK set is the issuer's public keys published as JSON at `jwks_uri`, each with a `kid`. The JWT header names the `kid`, so verifiers pick the right key and keys can rotate without downtime. Pin algorithms and never accept `alg: none`. Never let a token's header switch you from RSA to HMAC, because that's the algorithm-confusion attack.
</details>

<details><summary>Q8. Describe the authorization code flow with PKCE. Why PKCE?</summary>

1. The client creates a random `code_verifier` and sends `code_challenge = BASE64URL(SHA-256(verifier))` with the `/authorize` redirect, along with `state` (and `nonce` for OIDC).
2. The user logs in at the authorization server, which redirects back with a short-lived `code`.
3. The client exchanges the code plus the `code_verifier` at `/token`.
4. The AS checks the hash and returns the tokens.

PKCE ensures that a stolen authorization code is useless to anyone who didn't start the flow. OAuth 2.1 requires it for all clients, and Spring Authorization Server enables it by default since 7.0. The implicit and password grants are gone. The password grant was removed from Spring Security 7.
</details>

<details><summary>Q9. Where should an SPA keep its tokens? Explain the BFF pattern.</summary>

Not in `localStorage`/`sessionStorage`, because any XSS can read and exfiltrate them. With a BFF, a server-side component acts as a confidential OAuth client, runs code + PKCE, and stores the tokens in its session. The browser gets only an `HttpOnly; Secure; SameSite` cookie. The BFF attaches the access token when proxying API calls and refreshes it server-side. The cost is that cookies are back, so CSRF protection is required (`csrf.spa()` in 7.0), and the BFF holds state.
</details>

<details><summary>Q10. JWT vs opaque tokens, and how do you revoke a JWT?</summary>

A JWT is self-contained and validated locally (fast, no network call), but it stays valid until `exp`. An opaque token is a random reference validated by introspection at the AS, so it can be revoked instantly but costs a network call per request (cache it). JWT revocation options:
- a short TTL with refresh-token rotation;
- re-checking user state on refresh;
- a `jti` deny-list in Redis until `exp`;
- a per-user "tokens valid after" timestamp;
- opaque tokens for high-risk APIs;
- signing-key rotation as the nuclear option.
</details>

<details><summary>Q11. How does refresh-token rotation with reuse detection work?</summary>

Each refresh invalidates the presented refresh token and issues a new one in the same *family* (one family per login). If an already-used token is presented again, somebody holds a copy, so the server revokes the whole family and every holder must log in again. Store only hashes, make check-and-mark atomic (a conditional `UPDATE` or a lock), add an absolute family lifetime, and consider a short grace period for concurrent refreshes. Refresh tokens should be opaque, or at least never accepted as access tokens. The lab tests exactly this sequence.
</details>

<details><summary>Q12. When do you need CSRF protection, and why is it safe to disable for a JWT API?</summary>

CSRF exploits credentials the browser sends automatically (cookies, HTTP Basic, client certs). A malicious site can make the victim's browser POST to your app *with* those credentials. Any cookie- or session-based app, including a BFF, needs CSRF tokens and/or `SameSite` cookies. An API that authenticates *only* via an `Authorization: Bearer` header set by JavaScript isn't vulnerable, because a cross-site page can't attach that header. If the "JWT" lives in a cookie, you need CSRF protection again.
</details>

<details><summary>Q13. How does @PreAuthorize work internally, and when is it silently skipped?</summary>

`@EnableMethodSecurity` registers advisors (`AuthorizationManagerBeforeMethodInterceptor` for `@PreAuthorize`, and the after-interceptor for `@PostAuthorize`). AOP wraps matching beans in proxies (CGLIB by default). Before or after the call, `PreAuthorizeAuthorizationManager`/`PostAuthorizeAuthorizationManager` evaluate the SpEL with `MethodSecurityExpressionHandler` (`authentication`, `#param`, `returnObject`, `@bean`). A denial throws `AuthorizationDeniedException`, which `ExceptionTranslationFilter` turns into a 403. Because it's a proxy, it's skipped for self-invocation, private and final methods, and objects created with `new`. `@PostAuthorize` runs *after* the method, so use it only for reads.
</details>

<details><summary>Q14. How do you support several identity providers (multi-tenant) in one resource server?</summary>

Use `JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(...)`, or a custom `AuthenticationManagerResolver`, in `oauth2ResourceServer(o -> o.authenticationManagerResolver(resolver))`. It peeks at the unverified `iss` *only* to select a trusted issuer from an allow-list, then validates the signature with that issuer's JWKS. The per-issuer decoders are cached. Never fetch keys from an arbitrary `iss`. Then enforce tenant isolation on every data access (resource tenant equals token tenant).
</details>

<details><summary>Q15. What changed in Spring Security 7?</summary>

- **Removed:** `and()` (the lambda DSL only) and `authorizeRequests`. `AntPathRequestMatcher`/`MvcRequestMatcher` were replaced by `PathPatternRequestMatcher`. `AuthorizationManager#check` was replaced by `authorize`. The OAuth2 password grant is gone.
- **Moved:** the Access API (`AccessDecisionManager`/voters) into `spring-security-access`.
- **New:**
  - built-in **MFA** (`FactorGrantedAuthority` such as `FACTOR_PASSWORD`/`FACTOR_BEARER`, `@EnableMultiFactorAuthentication`, `AuthorizationManagerFactories.multiFactor()`);
  - `AuthorizationManagerFactory`, `Authentication.Builder`, and `csrf.spa()`;
  - `NimbusJwtEncoder` builders and Password4j encoders;
  - Spring Authorization Server merged in, with PKCE on by default.
- **Changed:** JWT `typ` validation by default, and Jackson 3 modules.
- **7.1 adds:** `anyOf` factor sets, conditional MFA, `RestClientOpaqueTokenIntrospector`, and `charset` in `WWW-Authenticate`.

In Boot 4 the starters are renamed `spring-boot-starter-security-oauth2-*`.
</details>



## 06 — Data JPA & Hibernate

_From [notes/06-data-jpa/jpa-hibernate.md](06-data-jpa/jpa-hibernate.md)_


<details><summary>Q1. What is the difference between JPA, Hibernate and Spring Data JPA?</summary>

**JPA** (Jakarta Persistence 3.2) is a *specification*: annotations and interfaces (`EntityManager`, `@Entity`, JPQL) with no implementation. **Hibernate** is a JPA *provider*: it implements `EntityManager` with its `Session`, generates SQL, does dirty checking and caching, and adds extras (`@BatchSize`, `@SoftDelete`, HQL). **Spring Data JPA** sits on top: it generates repository implementations from interfaces (`SimpleJpaRepository` plus query derivation) and integrates with Spring transactions. Between them, **Spring ORM** provides `JpaTransactionManager`, the shared `EntityManager` proxy, and exception translation into `DataAccessException`.
</details>

<details><summary>Q2. How does a repository interface with no implementation work at runtime?</summary>

`JpaRepositoriesRegistrar` registers a `JpaRepositoryFactoryBean` for each repository interface. The factory creates a JDK proxy whose target is a `SimpleJpaRepository` (for CRUD methods), with advice for exception translation, transactions, and a `QueryExecutorMethodInterceptor` for custom methods. Each custom method is resolved at startup by the lookup strategy: `@Query` first, then a named query, then derivation from the method name with `PartTree`. Errors therefore fail at startup. `SimpleJpaRepository` is `@Transactional(readOnly = true)` at class level, and its write methods override that with `@Transactional`.
</details>

<details><summary>Q3. Explain the entity lifecycle states.</summary>

**Transient:** `new`, unknown to JPA. **Managed:** in a persistence context, and changes are tracked and flushed. **Detached:** it was managed, but the context closed (the transaction ended, or `clear`/`detach`), so changes are ignored and lazy loads fail. **Removed:** scheduled for DELETE at flush. `persist` makes a transient entity managed. `merge` copies a detached entity's state onto a managed instance and *returns* that instance (the argument stays detached). `remove` marks a managed entity for deletion.
</details>

<details><summary>Q4. What is dirty checking, and when is SQL actually executed?</summary>

When an entity is loaded, Hibernate stores a snapshot of its state. At **flush**, it compares every managed entity to its snapshot and generates UPDATEs for the differences, so you never call `update()`, and calling `save()` on a managed entity is redundant. Flush happens before commit, before queries that touch affected tables (`FlushModeType.AUTO`), or on an explicit `flush()`. The action queue runs inserts, then updates, then collection changes, and deletes last. In a `readOnly` Spring transaction the flush mode is `MANUAL`, so changes are never written.
</details>

<details><summary>Q5. What is the N+1 problem, and how do you detect and fix it?</summary>

Loading N parents with one query and then touching a lazy association on each fires N more queries. Detect it by counting statements (Hibernate `Statistics.getPrepareStatementCount()`, datasource-proxy or p6spy in tests), not by reading code. Fixes: a `JOIN FETCH` or `@EntityGraph` for that use case (1 query), `@BatchSize` / `hibernate.default_batch_fetch_size` as a global safety net (1 + N/size), `SUBSELECT` fetching, or a DTO projection that loads no entities at all. Watch for `MultipleBagFetchException` with two `List`s, and for pagination combined with a collection fetch join.
</details>

<details><summary>Q6. What are the default fetch types, and why should every association be LAZY?</summary>

`@ManyToOne` and `@OneToOne` are **EAGER** by default. `@OneToMany`, `@ManyToMany` and `@ElementCollection` are LAZY. An EAGER to-one is loaded on every `find` and causes an extra SELECT per distinct parent after every JPQL query, which is N+1 even if you never use it, and you can't turn EAGER off per query. LAZY plus an explicit fetch plan per use case (entity graph or fetch join) gives you control. One niche exception: a LAZY to-one pointing at an entity with `@SoftDelete` is still loaded eagerly, because Hibernate must know whether the target counts as deleted.
</details>

<details><summary>Q7. Owning side vs inverse side: what does mappedBy do?</summary>

The owning side is the one that maps the foreign key (`@ManyToOne` with `@JoinColumn`, or the `@ManyToMany` side without `mappedBy`). Hibernate writes the FK based **only** on the owning side. `mappedBy` marks the inverse side (`@OneToMany(mappedBy = "customer")`), which is a read-only mirror: adding an order only to `customer.getOrders()` writes nothing. Keep both sides in sync with helper methods. A unidirectional `@OneToMany` without `mappedBy` creates a join table (or extra UPDATEs with `@JoinColumn`).
</details>

<details><summary>Q8. Explain @Transactional internals. Why doesn't it work on self-invocation or private methods?</summary>

`@EnableTransactionManagement` registers an advisor whose `TransactionInterceptor` wraps bean methods in a proxy. On a call, `TransactionAspectSupport.invokeWithinTransaction` reads the attributes, calls `PlatformTransactionManager.getTransaction` (`JpaTransactionManager` creates an `EntityManager`, begins the transaction, and binds the `EntityManager` and JDBC connection to the thread via `TransactionSynchronizationManager`), invokes the method, and then commits or calls `completeTransactionAfterThrowing`. Self-invocation (`this.method()`) never reaches the proxy. Private and final methods can't be overridden by CGLIB. Protected and package-private methods **do** work since Framework 6.0 with class-based proxies.
</details>

<details><summary>Q9. List the propagation types. When would you use REQUIRES_NEW, and what is the risk?</summary>

`REQUIRED` (join or create), `REQUIRES_NEW` (suspend and create an independent transaction), `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY` (throw if none), `NEVER` (throw if one exists), and `NESTED` (a savepoint; not enabled by default with `JpaTransactionManager`). Use `REQUIRES_NEW` for audit or attempt logs that must persist even if the business transaction rolls back. Risks: it needs a **second connection** while the first is held, which can deadlock a small pool under load, and its commit is independent, so an outer rollback doesn't undo it.
</details>

<details><summary>Q10. Which exceptions roll back a transaction by default? What is UnexpectedRollbackException?</summary>

`RuntimeException` and `Error` roll back. **Checked exceptions commit.** Change that with `rollbackFor`/`noRollbackFor`, or globally with `@EnableTransactionManagement(rollbackOn = ALL_EXCEPTIONS)` (Framework 6.2+). `UnexpectedRollbackException` happens when an inner `REQUIRED` method throws (marking the shared transaction rollback-only), the outer method catches it and returns normally, and the commit then discovers the rollback-only flag.
</details>

<details><summary>Q11. Optimistic vs pessimistic locking, and how do isolation levels relate to lost updates?</summary>

**Optimistic** (`@Version`): no DB lock is held. Each UPDATE adds `WHERE version = ?` and increments it, so 0 rows means a conflict → `ObjectOptimisticLockingFailureException`. Retry the whole transaction or report a 409. It's best when conflicts are rare and for long "think time" edits across HTTP requests (expose the version as an ETag). **Pessimistic** (`@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR NO KEY UPDATE` on Postgres with Hibernate 7.4) blocks other writers until commit. It's best when conflicts are frequent and retries are costly. It costs lock waits, held connections and deadlock risk, so keep transactions short and use `lock.timeout`/NOWAIT. For a single hot counter, an atomic `UPDATE ... SET stock = stock - 1 WHERE stock > 0` beats both.

**Isolation levels:** `READ_UNCOMMITTED` allows dirty reads. `READ_COMMITTED` (the Postgres/Oracle/SQL Server default) prevents dirty reads but allows non-repeatable reads, phantoms and lost updates. `REPEATABLE_READ` (the MySQL default; snapshot isolation in Postgres) prevents non-repeatable reads, and Postgres also prevents phantoms but allows write skew. `SERIALIZABLE` prevents everything, and Postgres SSI throws serialization failures you must retry. Most apps stay on READ COMMITTED and prevent lost updates with `@Version` or row locks rather than raising the isolation level.
</details>

<details><summary>Q12. What is Open Session in View, and why do we disable it?</summary>

`OpenEntityManagerInViewInterceptor` keeps the `EntityManager` open for the entire web request, so lazy associations can load during JSON serialisation or view rendering. Boot enables it by default and logs a warning. Problems: a connection is held for the whole request, lazy loads run outside transactions (auto-commit, N+1 hidden in the view layer), and data-access decisions leak out of the service layer. With `spring.jpa.open-in-view=false`, a lazy access after the service returns throws `LazyInitializationException`, which forces the service to fetch exactly what the use case needs or to return DTOs.
</details>

<details><summary>Q13. Why does IDENTITY disable batch inserts? Which ID strategy should you use?</summary>

With `IDENTITY` the database assigns the id during the INSERT, and Hibernate needs the id immediately to put the entity into the persistence context. So each `persist()` executes its own INSERT right away, and nothing can be grouped into a JDBC batch. `SEQUENCE` with a pooled optimizer (`allocationSize = 50` matching `INCREMENT BY 50`) fetches ids in blocks without inserting, so inserts are queued and sent in batches (`hibernate.jdbc.batch_size`, `order_inserts`). In the lab, 40 IDENTITY inserts take 40 statements and 40 SEQUENCE inserts take 4 or fewer. For distributed ids, UUIDv7 (`@UuidGenerator(style = VERSION_7)`) keeps inserts index-friendly.
</details>

<details><summary>Q14. How do you implement equals/hashCode on an entity?</summary>

Never base `hashCode` on a generated id: it changes when `persist()` assigns the id, so the entity gets lost in a `HashSet` (the lab proves `contains()` returns false for the same object). Options: use an immutable business key (`sku`, `email`); or use id-equality only when the id is non-null, plus a **constant** `hashCode` (`getClass()` or the entity class's hash); or assign a UUID in the constructor. Use `instanceof` and getters so Hibernate proxies compare correctly, and never use Lombok `@Data` on entities.
</details>

<details><summary>Q15. Page vs Slice vs Window (keyset): what does each cost?</summary>

`Page` runs the content query plus a `count(*)` (skipped when the total is obvious, e.g. a partial last page), so it's the right fit when the UI shows "page 3 of 120". `Slice` fetches `size + 1` rows and never counts, which suits infinite scroll. Both use `OFFSET`, which costs O(offset) and can skip or duplicate rows when data changes between requests. `Window` with `ScrollPosition.keyset()` filters `WHERE (sort keys) > (last seen)`. It costs the same at any depth and stays correct under inserts and deletes, but it needs a unique, stable sort and offers no "jump to page N".
</details>



## 05 — Web MVC & REST APIs

_From [notes/05-web-mvc-rest/rest-apis.md](05-web-mvc-rest/rest-apis.md)_


<details><summary>Q1. Walk me through what happens when a request hits a Spring MVC application.</summary>

Tomcat accepts the connection and runs the servlet **filter chain** (security, CORS, logging), which ends in the `DispatcherServlet`. `doDispatch` then:
1. Asks each `HandlerMapping` for a handler. `RequestMappingHandlerMapping` matches the path, method, headers, consumes/produces and (Framework 7) API version, and returns a `HandlerExecutionChain` (the `HandlerMethod` plus interceptors).
2. Finds a `HandlerAdapter` (`RequestMappingHandlerAdapter`).
3. Runs interceptor `preHandle`s.
4. The adapter resolves arguments with `HandlerMethodArgumentResolver`s. `@RequestBody` uses an `HttpMessageConverter` (Jackson 3), followed by validation. It invokes the controller and handles the return value with a `HandlerMethodReturnValueHandler`, which for `@ResponseBody`/`ResponseEntity` serializes the body through a converter chosen by content negotiation.
5. Runs `postHandle` (reverse order). It is skipped on exceptions.
6. On an exception, the `HandlerExceptionResolver` chain runs (`@ExceptionHandler` / `@ControllerAdvice` first).
7. Runs `afterCompletion` (always, reverse order).

Then control returns through the filters.
</details>

<details><summary>Q2. Filter vs HandlerInterceptor vs AOP: when do you use each?</summary>

A **Filter** is servlet-level. It sees every request, including static resources and error dispatches, can wrap the request/response, and runs outside Spring MVC (its exceptions don't reach `@ExceptionHandler`). Use it for security, CORS, correlation IDs/MDC, compression and request logging.

An **interceptor** runs inside the `DispatcherServlet` only for mapped requests, and knows the `HandlerMethod` (so it can read annotations). Use it for endpoint-aware checks, locale/tenant resolution and per-endpoint metrics.

**AOP** wraps bean methods regardless of the caller (HTTP, messaging, schedulers). Use it for transactions, caching, retries and auditing business methods.

Order for a request: filter → interceptor → (aspect around the controller/service) → back out.
</details>

<details><summary>Q3. @Controller vs @RestController, and how does a returned object become JSON?</summary>

`@RestController` is `@Controller` plus `@ResponseBody` on all methods. With `@Controller`, a `String` is a view name. With `@RestController`, the return value is written to the body. `RequestResponseBodyMethodProcessor` (or `HttpEntityMethodProcessor` for `ResponseEntity`) runs content negotiation: it takes the `Accept` header, intersects it with `produces` and the converters that can write the type, picks a media type, and calls that `HttpMessageConverter`. For JSON in Boot 4 that is `JacksonJsonHttpMessageConverter`, backed by Jackson 3's `JsonMapper`. No acceptable match gives 406.
</details>

<details><summary>Q4. MethodArgumentNotValidException vs HandlerMethodValidationException?</summary>

`MethodArgumentNotValidException` is thrown when a single `@Valid @RequestBody`/`@ModelAttribute` argument fails, and the method has no constraint annotations directly on its parameters. It carries a `BindingResult`. `HandlerMethodValidationException` comes from Spring MVC's built-in **method validation** (6.1+). It's triggered when any `@Constraint` (`@Min`, `@NotBlank`, `@NotNull`...) sits directly on a parameter or the return value. It validates all parameters, including nested `@Valid` ones, and carries a list of `ParameterValidationResult`s. Handle both, because adding `@NotNull` to a body parameter switches which one you get. Don't put class-level `@Validated` on controllers. That uses the AOP `MethodValidationPostProcessor` and throws `ConstraintViolationException` instead.
</details>

<details><summary>Q5. What is ProblemDetail and how do you implement consistent errors?</summary>

`ProblemDetail` is Spring's model of **RFC 9457** (which obsoletes 7807): `type`, `title`, `status`, `detail`, `instance`, plus extension members, served as `application/problem+json`. All Spring MVC exceptions implement `ErrorResponse`. Create **one** `@RestControllerAdvice` that extends `ResponseEntityExceptionHandler`. That gives ProblemDetail bodies for every framework exception. Add `@ExceptionHandler` methods for domain exceptions that return a `ProblemDetail` with a meaningful `type` URI, and override the validation handlers to add field errors as an extension property (rendered top-level). Keep controllers free of try/catch. Spring fills `instance` with the request path and omits `type` when it is `about:blank`.
</details>

<details><summary>Q6. PUT vs PATCH vs POST? Which are idempotent and why does it matter?</summary>

POST creates or triggers processing and is **not idempotent**: two calls may create two resources. PUT replaces the full representation at a known URI and is **idempotent**: N identical PUTs leave the same state. PATCH applies a partial change (JSON Merge Patch RFC 7396 or JSON Patch RFC 6902) and is not guaranteed idempotent. DELETE is idempotent (same final state, even if the second call returns 404/204). GET/HEAD/OPTIONS are **safe** and idempotent.

It matters because clients, proxies and gateways may **automatically retry** idempotent requests after timeouts, but must not blindly retry POST. That's why payment POSTs need idempotency keys.
</details>

<details><summary>Q7. How would you make POST /payments safe to retry?</summary>

Use an **idempotency key**. The client sends a unique `Idempotency-Key` (a UUID) per logical operation, the same on every retry. The server atomically claims the key (unique constraint or `SET NX`), scoped per account, together with a hash of the request. It performs the work, stores the response code and body, and replays them for retries (`Idempotent-Replayed: true`). Same key with a different body gives 422. Same key while the first is still running gives 409 (or wait and replay). Keys expire after ~24h. Ideally the key row is written in the same DB transaction as the payment, so they can't diverge. That's Stripe's design (section 7.1).
</details>

<details><summary>Q8. Explain ETags and conditional requests. How do they prevent lost updates?</summary>

An `ETag` is a validator for a representation (a hash or a version). On **GET**, the client sends `If-None-Match: "3"`. If it is unchanged the server returns **304** with no body, which saves bandwidth. In Spring, returning `ResponseEntity.ok().eTag(...)` makes `HttpEntityMethodProcessor` do this automatically. `ShallowEtagHeaderFilter` does it generically by hashing the body, but the controller still runs.

On **PUT/PATCH**, the client sends `If-Match: "3"`. The server applies the change only if the current version is still 3, otherwise it returns **412 Precondition Failed**. That's optimistic concurrency over HTTP. It prevents two editors overwriting each other's changes (lost update), and it maps naturally onto JPA `@Version`. You can require it with 428. Weak ETags (`W/`) never match `If-Match`.
</details>

<details><summary>Q9. Offset vs cursor pagination: which would you choose for an infinite feed and why?</summary>

Offset (`LIMIT/OFFSET`) allows random page access and totals, but deep pages get slower because the DB walks skipped rows. Rows also shift when data changes, causing duplicates or misses, and `COUNT(*)` is costly. Cursor/keyset pagination uses `WHERE (sort_key, id) < (:last) ORDER BY ... LIMIT n+1`. It's an index seek with constant cost, it's stable under inserts, and it needs no count. For an infinite feed, choose **cursor**, with an opaque cursor and a unique tie-breaker. Use offset for admin tables that need "page 7 of 12".
</details>

<details><summary>Q10. How do you version a REST API, and what does Spring Framework 7 add?</summary>

There are four strategies: URI path (`/v2/...`), header (`API-Version: 2`), query param, and media-type parameter. Version **only on breaking changes**. Framework 7 adds first-class support:
- `WebMvcConfigurer#configureApiVersioning` (or `spring.mvc.apiversion.*`) chooses the resolver.
- `@GetMapping(version = "1.0")` gives a fixed match, `"2.0+"` a baseline (that version and higher supported ones), and no version matches any.
- Versions are parsed semantically.
- Unsupported versions give 400 `InvalidApiVersionException`, and a missing version gives 400 unless you set a default.
- `StandardApiVersionDeprecationHandler` emits `Deprecation`/`Sunset`/`Link` headers.
- Clients use `ApiVersionInserter` on `RestClient`/`WebClient`.

With header versioning, add `Vary: API-Version` and allow the header in CORS.
</details>

<details><summary>Q11. Can you modify response headers in HandlerInterceptor.postHandle?</summary>

Not reliably for `@ResponseBody`/`ResponseEntity`. The body is written inside the `HandlerAdapter`, *before* `postHandle`. If the body overflowed the container's buffer (Tomcat's default is 8 KB), the response is already committed and your header is silently dropped. If it's small and still buffered, the header goes out. The lab proves both cases. That size-dependent behaviour is a trap. Set headers in `preHandle`, a `Filter`, the controller, or a `ResponseBodyAdvice` (which runs just before the converter writes).
</details>

<details><summary>Q12. What is the Richardson Maturity Model, and do you need HATEOAS?</summary>

The levels are:
- 0: one endpoint, RPC style.
- 1: resources.
- 2: HTTP verbs + status codes.
- 3: hypermedia controls (HATEOAS), where responses include links that describe available state transitions.

Most production APIs are level 2. HATEOAS (Spring HATEOAS: `EntityModel`, `linkTo(methodOn(...))`, HAL) helps long-lived public APIs with workflows, because clients follow links instead of hard-coding URLs and state rules. For internal APIs with a known client and an OpenAPI contract, it's usually not worth the payload and complexity. Say so, and explain the trade-off.
</details>

<details><summary>Q13. RestTemplate vs RestClient vs WebClient vs @HttpExchange?</summary>

- **`RestTemplate`**: the old template-method client. It's in maintenance mode.
- **`RestClient`** (6.1+): a fluent, synchronous client on the same infrastructure. It's the default for blocking code, and ideal with virtual threads.
- **`WebClient`**: reactive and non-blocking, for WebFlux or streaming.
- **`@HttpExchange` interfaces**: declarative clients (like Feign) backed by `RestClient` or `WebClient`. Framework 7 / Boot 4 add `@ImportHttpServices` groups configured via `spring.http.serviceclient.<group>.*`.

Inject Boot's `RestClient.Builder` so converters, observability and SSL are applied. Use `ApiVersionInserter` for versioned APIs.
</details>

<details><summary>Q14. What changes when you enable virtual threads in a Spring MVC app?</summary>

With `spring.threads.virtual.enabled=true`, Tomcat handles each request on a virtual thread (lab-proven), and `@Async`/the scheduler use virtual-thread executors. Blocking I/O no longer wastes platform threads, so thread-per-request scales to many thousands of concurrent requests with plain blocking code. The consequences:
- `server.tomcat.threads.*` sizing is ignored.
- The bottleneck moves to connection pools and downstream services, so add limits.
- `synchronized` blocks pin carriers on Java 21–23 (fixed in JDK 24, JEP 491).
- `ThreadLocal` caches lose their pooling benefit.
- Virtual threads are daemon threads (`spring.main.keep-alive=true` for scheduler-only apps).

It doesn't make CPU-bound work faster.
</details>

<details><summary>Q15. What changed for JSON in Spring Boot 4?</summary>

Jackson 3 is the default:
- Packages are `tools.jackson.*`, and annotations stay in `com.fasterxml.jackson.annotation`.
- Boot auto-configures an immutable `JsonMapper`, so to override it you define a `JsonMapper` bean (an `ObjectMapper` bean is no longer enough) or a `JsonMapperBuilderCustomizer`.
- `@JsonComponent` is now `@JacksonComponent`, and `spring.jackson.read/write.*` moved under `spring.jackson.json.*`.
- Exceptions are unchecked (`JacksonException`).
- Defaults changed: ISO dates, unknown properties ignored, alphabetical property order, `java.time` built in.
- Converters are customized with `ServerHttpMessageConvertersCustomizer`.

Jackson 2 is available only through the deprecated `spring-boot-jackson2` module.
</details>



## 09 — Caching & Redis

_From [notes/09-caching-redis/caching-redis.md](09-caching-redis/caching-redis.md)_


<details><summary>Q1. How does @Cacheable work internally?</summary>

`@EnableCaching` registers `AnnotationCacheOperationSource`, `CacheInterceptor` and `BeanFactoryCacheOperationSourceAdvisor`. The auto-proxy creator wraps matching beans in a (CGLIB, by default) proxy after initialization. On a call, `CacheInterceptor` (via `CacheAspectSupport.execute`) resolves the cached `CacheOperation`s, evaluates `condition`, computes the key (SpEL `key` or the `KeyGenerator`), and asks `CacheManager.getCache(name).get(key)`. On a hit it returns without invoking the target. On a miss it invokes the target, evaluates `unless` against `#result`, and `put`s the value. Because it's a proxy, self-invocation, private/final methods and `new`-created objects aren't cached.
</details>

<details><summary>Q2. What is the default cache key? Why is it risky with Redis?</summary>

`SimpleKeyGenerator`: no params gives `SimpleKey.EMPTY`, one param is the param itself, and multiple params give `SimpleKey(params)`. On Redis the key is stringified, e.g. `quotes::SimpleKey [1, EUR]`. It's fragile: reordering or adding a parameter changes all keys, two methods sharing a cache with the same args collide, and type differences (`Integer` vs `Long`) can matter for local caches. Always give an explicit `key` SpEL or a custom `KeyGenerator`, e.g. to add a tenant id.
</details>

<details><summary>Q3. condition vs unless?</summary>

`condition` is evaluated before the method runs, and if false the cache is bypassed entirely (no lookup, no put). `unless` is evaluated after the method runs, can reference `#result`, and only vetoes the put. `unless = "#result == null"` is the common way to avoid caching misses. `sync = true` doesn't support `unless`.
</details>

<details><summary>Q4. What does sync = true do, and does it protect against a stampede on Redis?</summary>

It makes the interceptor call `Cache.get(key, valueLoader)` so the cache implementation can let only one caller load while the others wait. Caffeine and `ConcurrentMapCache` do this per JVM (`computeIfAbsent`). On Spring Data Redis 4.x, `RedisCache.get(key, loader)` isn't synchronized. With Boot's default non-locking writer, 8 concurrent misses run the loader 8 times (proven in the lab). With `RedisCacheWriter.create(cf, c -> c.enableLocking())` misses collapse to one load across all JVMs, but the lock is per *cache* (`products~lock`), so a slow load also stalls hits on other keys. Restrictions: `sync` can't be combined with `unless`, multiple caches, or other cache operations on the method.
</details>

<details><summary>Q5. Explain cache-aside, read-through, write-through, write-behind and refresh-ahead.</summary>

- **Cache-aside:** the app reads the cache, and on a miss loads from the DB and populates the cache. On write it updates the DB and evicts. This is `@Cacheable` + `@CacheEvict`.
- **Read-through:** the cache itself loads on a miss via a registered loader (Caffeine `LoadingCache`, JCache `CacheLoader`).
- **Write-through:** writes go to the cache, which synchronously writes the DB, so both are updated before acknowledging.
- **Write-behind:** the cache acknowledges and flushes to the DB asynchronously in batches. It's fast, but can lose data.
- **Refresh-ahead:** hot entries are reloaded before expiry (Caffeine `refreshAfterWrite`), so readers never see the miss.
</details>

<details><summary>Q6. Update the DB then delete the cache, or delete then update? What race remains?</summary>

Update the DB, then delete. Delete-first lets a concurrent reader re-cache the old value before the update commits. Delete-after-write still has a rare race: a reader misses, reads the old row, the writer commits and deletes, then the reader `SET`s the stale value. Mitigations: always set a TTL (it bounds the staleness), delayed double delete (delete again after about 500 ms), evict after commit (cache advice outside the transaction, or `transactionAware()`), versioned compare-and-set, or CDC-based invalidation. Prefer evict over put, because concurrent puts can leave the older value.
</details>

<details><summary>Q7. Stampede vs penetration vs avalanche, and how do you handle each?</summary>

- **Stampede:** a hot key expires and many concurrent misses hit the DB. Handle it with `sync`/locks (single-flight), refresh-ahead, or probabilistic early expiration.
- **Penetration:** lookups for non-existent keys always miss. Handle it with null caching with a short TTL, a Bloom filter in front, and input validation.
- **Avalanche:** many keys expire at once, or the cache goes down. Handle it with TTL jitter, staggered warm-up, Redis HA, multi-level caching, and DB circuit breakers or rate limits.
</details>

<details><summary>Q8. Why is Redis single-threaded yet fast, and what does that imply?</summary>

Data is in memory, commands are simple, and one event loop with I/O multiplexing avoids locking and context switches. Redis 6+ can offload socket I/O to threads, but command execution remains single-threaded. Implications: every command and Lua script is atomic (great for counters, locks and rate limits), and one slow command (`KEYS *`, a huge `HGETALL`, a long script) blocks all clients. Use `SCAN` and watch `SLOWLOG`.
</details>

<details><summary>Q9. RDB vs AOF? Which eviction policy for a cache?</summary>

RDB takes periodic fork-based snapshots: compact and fast to restart, but you lose writes since the last snapshot. AOF logs every write with `appendfsync everysec` (about 1 s of loss), and gets bigger and is rewritten periodically. Production stores often use both. A pure cache can skip persistence. The `maxmemory-policy` default is `noeviction`, where writes fail when full. For caches use `allkeys-lru` or `allkeys-lfu`. `volatile-*` policies only evict keys with a TTL.
</details>

<details><summary>Q10. Sentinel vs Cluster?</summary>

Sentinel provides HA for a single primary dataset: sentinels monitor it, vote on a quorum, promote a replica, and tell clients the new primary. It doesn't scale writes. Cluster shards the keyspace into 16384 hash slots across several primaries (each with replicas), handles failover itself, and clients follow `MOVED`/`ASK` redirects. Multi-key operations and Lua scripts must stay within one slot, so use hash tags like `{user:42}`. Replication is asynchronous in both, so a failover can lose recent writes.
</details>

<details><summary>Q11. Lettuce vs Jedis?</summary>

Lettuce (Boot's default) is Netty-based and non-blocking. One connection is thread-safe and shared, and it offers sync, async and reactive APIs, so it is required for `ReactiveRedisTemplate`. Pooling is optional and only useful for blocking commands or transactions. Jedis uses blocking sockets and one connection per thread, so it needs a pool. It's simple and fast, but has no reactive API. Switch to it by excluding `lettuce-core` and adding `jedis`.
</details>

<details><summary>Q12. How do you build a distributed lock on Redis, and what's the Redlock debate?</summary>

`SET lock:res <random token> NX PX <ttl>` to acquire atomically with a lease. Release with a Lua compare-and-delete so you never delete someone else's lock. The TTL means a paused holder can lose the lock while still working, so correctness requires fencing tokens checked by the resource. Redlock acquires the lock on a majority of N independent masters. Kleppmann argued it relies on timing assumptions and lacks fencing, and antirez countered. Use Redis locks for efficiency. For correctness use consensus (ZooKeeper, etcd) or DB locks plus fencing. Libraries: Redisson (`RLock` with a watchdog lease extension) and ShedLock (single-node `@Scheduled` execution).
</details>

<details><summary>Q13. Compare rate-limiting algorithms and how you'd implement one in Redis.</summary>

- **Fixed window:** `INCR` + `EXPIRE` on the first hit, atomically in Lua. Cheap, but allows 2× bursts at boundaries.
- **Sliding log:** a ZSET of timestamps (`ZADD`, `ZREMRANGEBYSCORE`, `ZCARD`). Exact, but memory-heavy.
- **Sliding window counter:** a weighted current plus previous window. O(1) and accurate enough.
- **Token bucket:** a hash of tokens plus the last refill, refilled in Lua. Allows bursts up to capacity with a steady rate. Spring Cloud Gateway's `RedisRateLimiter` uses it.

Always make check-and-update atomic, decide fail-open vs fail-closed, and return 429 with `Retry-After`.
</details>

<details><summary>Q14. How do @Transactional and @Cacheable interact on the same method?</summary>

Both are advisors on one proxy. Both `@EnableCaching` and `@EnableTransactionManagement` default to `LOWEST_PRECEDENCE`, so set their `order` explicitly. With caching outside (lower order value), cache hits don't open a transaction, and evicts run after commit, so readers can't re-cache uncommitted-old data. With caching inside, puts and evicts happen before commit, and a rollback leaves the cache inconsistent. If you can't control the order, `RedisCacheManager.builder().transactionAware()` (`TransactionAwareCacheDecorator`) defers put/evict to after commit.
</details>

<details><summary>Q15. What changed for Redis caching in Boot 4 / Spring Data Redis 4?</summary>

- Jackson 3 serializers: `GenericJacksonJsonRedisSerializer` and `JacksonJsonRedisSerializer`, with the Jackson 2 versions deprecated. The generic one no longer enables default typing.
- With Lettuce, the cache writer does async put/evict unless `immediateWrites()` is set.
- `getTtl()` was replaced by `TtlFunction`.
- `RedisCache.get(key, loader)` isn't synchronized, so `sync` needs a locking writer.
- Boot's modular `spring-boot-cache` / `spring-boot-data-redis` modules and `*-test` starters, with renamed `DataRedisProperties` / `DataRedisConnectionDetails`.
- `spring.session.redis.*` was renamed to `spring.session.data.redis.*`.
- `spring.data.redis.masterreplica.nodes`.
- Lettuce-native observability.
- Testcontainers 2.
</details>


