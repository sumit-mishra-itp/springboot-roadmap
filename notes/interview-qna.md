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


