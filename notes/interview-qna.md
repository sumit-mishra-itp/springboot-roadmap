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



## 10 — Observability

_From [notes/10-observability/observability.md](10-observability/observability.md)_


<details><summary>Q1. What are the three pillars of observability, and how do you correlate them?</summary>

Metrics (cheap aggregates for detection and alerting), traces (per-request causal timing across services, for localization) and logs (discrete events with detail, for explanation). Profiles are often called the fourth. Correlation comes from shared identifiers. The trace id is put into the MDC, so every log line carries `traceId`/`spanId`. Metrics carry exemplars (sample trace ids on histogram buckets). Common resource attributes (`service.name`, `deployment.environment`) are shared by all signals. In an incident you go from metric spike → exemplar → trace → logs filtered by trace id.
</details>

<details><summary>Q2. Which actuator endpoints are exposed by default, and how do you secure them?</summary>

Over HTTP and JMX, only `health` is exposed by default. Exposure (`management.endpoints.web.exposure.include`) decides URLs. Access (`management.endpoint.<id>.access` = none/read-only/unrestricted, since 3.4) decides whether the endpoint exists at all and whether write operations are allowed. `shutdown` and `heapdump` are restricted by default. To secure them, use a dedicated `SecurityFilterChain` matched by `EndpointRequest.toAnyEndpoint()`: permit `health` (and maybe `info`) and require an ops role for the rest. Optionally move actuator to `management.server.port` on an internal network. Never expose `*` publicly: `heapdump` and `env` leak secrets.
</details>

<details><summary>Q3. Explain liveness vs readiness. Why shouldn't liveness check the database?</summary>

Liveness means "this process is broken, restart it". Readiness means "don't route traffic to me right now". Boot backs them with `LivenessState` and `ReadinessState` and exposes them as health groups (on by default in Boot 4). If liveness includes a shared dependency such as the DB, a DB blip fails liveness on *every* pod at once. Kubernetes restarts them all, and the cold starts stampede the recovering DB, which is a cascading failure. A dependency outage should at most affect readiness, or better, be handled with circuit breakers and degraded responses. Use a startup probe for slow boots.
</details>

<details><summary>Q4. What happens to readiness during graceful shutdown?</summary>

On SIGTERM, Boot (graceful shutdown is on by default) sets readiness to `REFUSING_TRAFFIC`, so the readiness group returns `OUT_OF_SERVICE`/503. The web server stops accepting new requests and gives in-flight ones up to `spring.lifecycle.timeout-per-shutdown-phase` to finish. Because endpoint propagation in Kubernetes is asynchronous, add a `preStop` sleep so the pod leaves the Service endpoints before Tomcat stops accepting. `terminationGracePeriodSeconds` must exceed preStop plus the shutdown timeout.
</details>

<details><summary>Q5. Explain Micrometer's architecture: MeterRegistry, the composite registry, dimensional metrics.</summary>

Micrometer is a vendor-neutral facade, like SLF4J for metrics. You create meters (Counter, Timer, Gauge, DistributionSummary, LongTaskTimer) on a `MeterRegistry`. Each backend has a registry implementation (Prometheus, OTLP, Datadog...) with its own naming convention and push/pull model. Boot creates one registry per backend on the classpath and wraps several in a `CompositeMeterRegistry` (with exactly one, the bean is that registry itself). Meters are dimensional: name plus tags, so one metric has many series you can slice and aggregate. `MeterFilter`s can rename, deny, cap tags and configure histograms. `MeterBinder`s register meters for other components.
</details>

<details><summary>Q6. Percentiles vs histograms: which would you use for a fleet-wide p99 SLO?</summary>

Histograms. `publishPercentiles` computes quantiles inside each JVM over a decaying window. You can't aggregate them across instances (averaging p99s is mathematically wrong), and you can't pick a different percentile later. `publishPercentileHistogram` emits cumulative `_bucket{le}` counters, which can be summed across pods and queried with `histogram_quantile(0.99, sum by (le)(rate(...[5m])))`. Add `serviceLevelObjectives(...)` buckets at your SLO thresholds so "% of requests under 500 ms" is exact. Trim the bucket range with min/max expected values to control series count.
</details>

<details><summary>Q7. What is the Observation API, and why does it exist?</summary>

It's Micrometer's single instrumentation abstraction (Boot 3+). You start and stop an `Observation` with a name, a contextual name, and low/high-cardinality key values. Registered `ObservationHandler`s react: `DefaultMeterObservationHandler` records a timer and a long-task timer using the low-cardinality keys, and the tracing handlers create a span with all keys and propagate context. So you instrument once and get metrics *and* traces, instead of the old Micrometer + Sleuth double instrumentation. Spring Framework instruments MVC, WebFlux, RestClient, JMS, scheduling and more with it. You customize through `ObservationConvention`, `ObservationFilter`, `ObservationPredicate` and `management.observations.*` properties.
</details>

<details><summary>Q8. What's the difference between low- and high-cardinality key values? Give examples.</summary>

Low-cardinality values come from a small bounded set (`method`, `status`, `outcome`, `tier`, `region`). They become metric tags and span attributes. High-cardinality values are unbounded (`order.id`, `user.id`, full URL). They are added only to spans, because every distinct tag value creates a new time series, and unbounded ones exhaust the metrics backend's memory. Spring tags HTTP metrics with the URI *template*, not the raw path, for this reason. Guard any client-controlled tag with `MeterFilter.maximumAllowableTags`.
</details>

<details><summary>Q9. How does a trace id travel from service A to service B?</summary>

A's server instrumentation extracts the incoming `traceparent` (or starts a new trace) and puts the span in scope. When A calls B through an *instrumented* client (from the auto-configured `RestClient.Builder`, `WebClient.Builder` or `RestTemplateBuilder`), `PropagatingSenderTracingObservationHandler` creates a CLIENT span and the propagator injects `traceparent: 00-<traceId>-<clientSpanId>-<flags>`. B's receiver handler extracts it and creates a SERVER span with the same trace id and the client span as its parent. Across threads, the context moves through context-propagation (`ContextSnapshot`, task decorators). Over messaging, it moves as Kafka or JMS headers.
</details>

<details><summary>Q10. Head vs tail sampling: what's Boot's default?</summary>

Head sampling decides at the root span and propagates the decision through the sampled flag. It's cheap, but blind to outcome. Boot's default probability is 0.1 (10%), with OTel's `parent-based-trace-id-ratio` sampler, so children honor the parent's decision. Tail sampling is done in the OTel Collector after a trace completes: you can keep all errors and slow traces and a small share of the rest. It costs buffering and needs trace-aware load balancing. Many teams combine a high head rate in the app with tail sampling in the collector.
</details>

<details><summary>Q11. Micrometer Tracing bridge vs the OpenTelemetry Java agent: when would you pick each?</summary>

**Micrometer Tracing with the OTel bridge** (Boot's `spring-boot-starter-opentelemetry`) uses Spring's built-in Observation instrumentation, configured via Spring properties. One API gives metrics and traces, it is native-image friendly with no bytecode manipulation, and it is supported by the Spring team. It covers only what Spring and Micrometer instrument; for other libraries (raw JDBC, some clients) you add instrumentation.

**The OTel Java agent** (`-javaagent:opentelemetry-javaagent.jar`) instruments hundreds of libraries automatically with zero code and is configured with `OTEL_*` env vars. It's good for polyglot platform teams and legacy apps. The costs: startup overhead, no GraalVM native image, possible duplicate spans or metrics if Spring's observations are also active, and version coupling to the libraries it patches.

There is also the community **OTel Spring Boot starter** (from OTel, not Spring). Don't run the agent and Boot's tracing together without disabling one of them.
</details>

<details><summary>Q12. How do you get structured JSON logs with trace ids in Boot, and change log levels at runtime?</summary>

Set `logging.structured.format.console=ecs` (or `logstash`, `gelf`, or a custom `StructuredLogFormatter`). Boot 3.4+ writes JSON, and every MDC entry, including the `traceId` and `spanId` that tracing puts there, becomes a field. Tune it with `logging.structured.json.*`. For runtime levels, `POST /actuator/loggers/com.acme {"configuredLevel":"DEBUG"}`. It changes the live `LoggingSystem` on that instance only and doesn't survive a restart. Reset it with `null`. Secure the endpoint.
</details>

<details><summary>Q13. Explain RED and USE, and how they map to Spring metrics.</summary>

- **RED** (for request-driven services): Rate, Errors, Duration. All three come from `http_server_requests_seconds` (`_count` rate, `outcome="SERVER_ERROR"` ratio, histogram buckets for latency), and likewise `http.client.requests` for dependencies.
- **USE** (for resources): Utilization, Saturation, Errors. Examples: CPU (`process_cpu_usage`), pools (`hikaricp_connections_active` vs `_max`, `hikaricp_connections_pending` for saturation, `executor_*`, `tomcat_threads_busy`), memory and GC (`jvm_memory_used_bytes`, `jvm_gc_pause_seconds`).

Dashboards are typically RED per endpoint on top and USE per resource below.
</details>

<details><summary>Q14. What are SLIs, SLOs and error budgets? Write an alert.</summary>

An **SLI** is a measured ratio of good events, e.g. "requests under 500 ms and not 5xx / all requests". An **SLO** is the target over a window, e.g. 99.9% over 30 days. The **error budget** is 1 − SLO (0.1%, about 43 minutes a month). Spend it on releases, and freeze risky changes when it's exhausted. Alert on **burn rate** rather than raw thresholds. For example, page if the 1-hour error ratio exceeds 14.4× the budget rate *and* the 5-minute one also does (multi-window, multi-burn-rate), which catches fast burns quickly without flapping. A simple Prometheus rule: `sum(rate(http_server_requests_seconds_count{outcome="SERVER_ERROR"}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05` with `for: 5m`. Alert on symptoms users feel, not on causes like CPU.
</details>

<details><summary>Q15. What changed for observability in Spring Boot 4?</summary>

- A new `spring-boot-starter-opentelemetry` (OTel SDK, Micrometer bridge, OTLP exporters).
- Modular `spring-boot-health` / `-micrometer-*` / `-opentelemetry` modules with new packages (e.g. `org.springframework.boot.health.contributor`).
- Tests use `@AutoConfigureMetrics` and `@AutoConfigureTracing` instead of the removed `@AutoConfigureObservability`. Without them, there is no Prometheus endpoint, and tracing doesn't propagate or export.
- `management.tracing.enabled` was renamed to `management.tracing.export.enabled`.
- Liveness/readiness probes are on by default.
- `@MeterTag` works on `@Timed`/`@Counted`.
- The RestClient builder lives in `spring-boot-starter-restclient`.
- OTel + Zipkin auto-configuration is deprecated, with removal in 4.2.
- Versions: Micrometer 1.17 / Tracing 1.7 in Boot 4.1.
</details>



## 12 — Async, scheduling & Batch

_From [notes/12-async-batch/async-scheduling-batch.md](12-async-batch/async-scheduling-batch.md)_


<details><summary>Q1. How does @Async work under the hood?</summary>

`@EnableAsync` registers `AsyncAnnotationBeanPostProcessor`. In `postProcessAfterInitialization`, it wraps beans that have `@Async` methods in a proxy whose advice is `AnnotationAsyncExecutionInterceptor`. On a call, the interceptor resolves an executor and submits the invocation as a `Callable`:
1. the `@Async` qualifier;
2. otherwise `AsyncConfigurer.getAsyncExecutor()`;
3. otherwise a unique `TaskExecutor` bean;
4. otherwise a bean named `taskExecutor`;
5. otherwise a new `SimpleAsyncTaskExecutor`, a fresh thread per call (proven by `withoutAnyExecutorBean_asyncFallsBackToSimpleAsyncTaskExecutor_aNewThreadPerCall`).

It then returns immediately: a future for `Future`/`CompletableFuture` methods, or nothing for `void`. Because it's proxy-based, self-invocation, `private` methods and calls from `@PostConstruct` are not async.
</details>

<details><summary>Q2. What happens to an exception thrown in an @Async method?</summary>

If the method returns `CompletableFuture`/`Future`, the exception completes the future exceptionally. `get()` throws `ExecutionException`, `join()` throws `CompletionException`, and you can recover with `exceptionally`/`handle`. If the method is `void`, nobody can receive it, so it goes to the `AsyncUncaughtExceptionHandler`. The default `SimpleAsyncUncaughtExceptionHandler` only logs it. Register your own via `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` to count, alert or dead-letter.
</details>

<details><summary>Q3. Does @Transactional or the SecurityContext propagate into an @Async method?</summary>

No. Both are bound to the thread through `ThreadLocal`s (`TransactionSynchronizationManager`, `SecurityContextHolder`). The async method runs outside the caller's transaction and can't see its uncommitted rows. If it's `@Transactional` itself, it starts its own. For security, use `DelegatingSecurityContextAsyncTaskExecutor` or a `TaskDecorator` that wraps with `DelegatingSecurityContextRunnable`. For "do X after the save", use `@TransactionalEventListener(AFTER_COMMIT)` with `@Async`.
</details>

<details><summary>Q4. What is a TaskDecorator and when do you need one?</summary>

It's a hook on an executor that wraps every submitted `Runnable`. `decorate()` runs on the submitting thread, so it can snapshot `ThreadLocal` state (MDC, security, locale, tenant) and restore it on the worker thread around `run()`. It should restore the worker's previous state afterwards, not just clear it, because with `CallerRunsPolicy` the worker can be the caller. Boot applies `TaskDecorator` beans to its auto-configured executor and scheduler, combining several into a `CompositeTaskDecorator` in Boot 4. You must set it yourself on executors you build. `spring.task.execution.propagate-context=true` adds Micrometer's `ContextPropagatingTaskDecorator`.
</details>

<details><summary>Q5. fixedRate vs fixedDelay vs cron? Can a scheduled task overlap with itself?</summary>

- `fixedDelay` measures from the end of one run to the start of the next.
- `fixedRate` measures start to start.
- `cron` fires at calendar times. Spring cron has 6 fields with seconds first, and supports `L`, `W`, `#`, macros, a `zone`, and `"-"` to disable.

On Boot's default scheduler (one thread), a `fixedRate` run that takes longer than the rate makes the next run late, not concurrent. The lab proves a 50 ms rate with a 200 ms task never overlaps, even with `pool.size=4`. Overlap happens only when each tick gets a new thread: `@Async` on the method, or virtual threads (`SimpleAsyncTaskScheduler`), which the lab also proves. Also, all `@Scheduled` methods share that one thread by default, so one slow task delays all the others.
</details>

<details><summary>Q6. You run 3 replicas. How do you stop a @Scheduled job running 3 times?</summary>

Take a distributed lock per execution. ShedLock is the common choice: `@SchedulerLock(name, lockAtMostFor, lockAtLeastFor)` backed by a JDBC, Redis or Mongo lock table. `lockAtMostFor` covers a dead holder, and `lockAtLeastFor` covers clock skew between fast nodes. The alternatives:
- Quartz in clustered JDBC mode, with misfire handling and persistent triggers;
- leader election;
- moving the trigger out of the app, e.g. a Kubernetes `CronJob` with `concurrencyPolicy: Forbid`.

Spring Batch adds a second guard: the same `JobInstance` can't run concurrently, and a completed instance can't run again, as long as the identifying parameters are deterministic (a business date).
</details>

<details><summary>Q7. What does spring.threads.virtual.enabled=true change, and when shouldn't you use virtual threads?</summary>

On Java 21+ it switches Boot's `applicationTaskExecutor` to a virtual-thread `SimpleAsyncTaskExecutor`, where pool properties no longer apply, and the scheduler to `SimpleAsyncTaskScheduler`. It also switches the embedded web server's request handling, Boot-configured messaging listeners and JDK `HttpClient`-based clients. It doesn't change executors you build yourself.

Avoid virtual threads for:
- CPU-bound work, since the carriers are limited to the cores;
- code that relied on pool size as a throttle for a downstream;
- heavy `ThreadLocal` caches;
- on Java 21–23, `synchronized` sections around blocking I/O, because they pin the carrier. JEP 491 fixed that in Java 24, which is why Boot recommends 24+.

Virtual threads are daemon threads, so set `spring.main.keep-alive=true` for apps with no other non-daemon thread.
</details>

<details><summary>Q8. Explain JobInstance vs JobExecution vs StepExecution.</summary>

- A `JobInstance` is a logical run: the job name plus the hash of its identifying `JobParameters`, e.g. "the import for 2026-11-17".
- A `JobExecution` is one attempt at that instance. A failed instance that is restarted gets a second `JobExecution` under the same instance.
- A `StepExecution` is one attempt at one step inside a `JobExecution`. It holds the read/write/filter/skip/commit/rollback counters and its own persisted `ExecutionContext`.

Launching completed identifying parameters again throws `JobInstanceAlreadyCompleteException`. Launching failed ones restarts the instance.
</details>

<details><summary>Q9. How does Spring Batch make a restart continue from the last committed chunk without duplicates?</summary>

Each chunk runs in one transaction that commits three things:
- the writer's business writes;
- the step's updated counters;
- the reader's position, which an `ItemStream` reader saves into the step `ExecutionContext` (e.g. `transactionReader.read.count`).

These go to the JobRepository atomically. If item 25 fails, chunk 21–30 rolls back, and the context still says 20. On restart, with the same identifying parameters, completed steps are skipped and the reader's `open()` repositions to item 21. The lab asserts 20 rows after the crash, a `readCount` of 15 on restart, and 35 distinct rows. Writers to non-transactional systems must still be idempotent.
</details>

<details><summary>Q10. Skip vs retry vs filter in a chunk step?</summary>

- **Filter:** the processor returns `null`. The item is dropped as a normal outcome (`filterCount`).
- **Skip:** a declared skippable exception is thrown, and the item is dropped and counted (`readSkipCount`/`processSkipCount`/`writeSkipCount`) up to a skip limit or `SkipPolicy`. Exceeding the limit fails the step.
- **Retry:** a declared retryable (transient) exception makes Batch re-attempt the operation up to the retry limit with back-off. If the retries are exhausted, the item can still be skipped if it's also skippable.

In Batch 6 retry uses Framework 7 core retry, and the new `ChunkOrientedStep` skips or retries the item without rolling back the chunk. The lab shows `rollbackCount == 0`, where Batch 5 rolled back and re-processed the chunk.
</details>

<details><summary>Q11. Multi-threaded step vs partitioning vs remote chunking: which would you pick?</summary>

- **Multi-threaded step:** one step with a `taskExecutor`. It's simple, but readers are shared, and classically it isn't restartable (`saveState(false)`). Batch 6's new model keeps reading and writing on one thread and parallelises the processor.
- **Partitioning:** a manager step splits the data (per file, per ID range) into partitions, each a full `StepExecution` with its own reader and context. It's restartable per partition and can be local (thread pool) or remote (workers over messaging).
- **Remote chunking:** the manager reads and sends chunks to workers that process and write. Use it when processing or writing dominates.
- **Parallel steps:** independent steps run in a `split` flow.

For a restartable file import, pick partitioning (the lab's choice).
</details>

<details><summary>Q12. What changed for Spring Batch in Boot 4 / Batch 6?</summary>

- `JobLauncher` is deprecated: `JobOperator` extends it, so use `jobOperator.start(job, params)`.
- `JobRepository` extends `JobExplorer`.
- `@EnableBatchProcessing`/`DefaultBatchConfiguration` default to a resourceless repository. Opt into a DB with `@EnableJdbcJobRepository`.
- In Boot, `spring-boot-starter-batch` is in-memory, and `spring-boot-starter-batch-jdbc` is needed for a DB repository.
- `@EnableBatchProcessing` still makes Boot's auto-config back off.
- Packages moved: `core.job`, `core.step`, `core.job.parameters`, and `infrastructure.*` for all item and repeat APIs.
- `chunk(int).transactionManager(tm)` builds the new `ChunkOrientedStep`, and the `chunk(int, tm)` form is deprecated.
- Retry is on Framework 7 core retry.
- `JobParameter` is a record, and `BATCH_JOB_SEQ` became `BATCH_JOB_INSTANCE_SEQ`.
- v5 executions can't be restarted on v6.
- Tests use `JobOperatorTestUtils.startJob()`.
</details>

<details><summary>Q13. Your @Async pool is configured with max-size 50 but only ever uses 8 threads. Why?</summary>

`ThreadPoolExecutor` grows beyond the core size only when the queue is full, and the default queue capacity is unbounded (`Integer.MAX_VALUE`). So it stays at the core size (Boot's default is 8) while tasks pile up in the queue. Set a bounded `queue-capacity`, and choose a rejection policy for when both the queue and max are full. `CallerRunsPolicy` gives back-pressure, and the default `AbortPolicy` throws `TaskRejectedException`.
</details>

<details><summary>Q14. When would you choose Spring Batch over a Kafka streaming consumer, and vice versa?</summary>

Choose **Batch** for bounded datasets with a natural window:
- partner files, regulatory and month-end reports, migrations, backfills, reconciliation;
- cases where you need whole-dataset checks (trailer totals), restart-by-date semantics, and throughput over latency.

Choose **streaming** when consumers must react within seconds:
- fraud, balances, notifications;
- cases where many independent consumers need the same events.

Streaming brings offsets, idempotent consumers, dead-letter topics and lag monitoring in place of the JobRepository. Mature systems use both: events for real time, and batch for reconciliation and partner files.
</details>

<details><summary>Q15. How do you compose several CompletableFutures with a timeout and fallbacks, and on which thread do the callbacks run?</summary>

Start all calls first, then compose:
- `thenCombine` for two results;
- `allOf(...)` plus `join()` on each for N results;
- `thenCompose` to chain dependent async calls;
- per call, `orTimeout`/`completeOnTimeout` and `exceptionally`/`handle` for fallbacks.

Non-`Async` callbacks run on the thread that completes the future (the worker), or immediately on the caller if it's already complete. `*Async` variants without an executor use `ForkJoinPool.commonPool()`, which must not be used for blocking I/O, so pass an executor (a virtual-thread executor is ideal for I/O).
</details>



## 08 — Testing

_From [notes/08-testing/testing.md](08-testing/testing.md)_


<details><summary>Q1. How does the Spring TestContext framework decide whether two test classes share an ApplicationContext?</summary>

It builds a `MergedContextConfiguration` for each class and uses it as the key into a static, JVM-wide `DefaultContextCache`. The key contains:
- the configuration classes and locations;
- the context initializers;
- the context loader;
- the parent context;
- active profiles;
- `@TestPropertySource` locations and inlined properties;
- the resource base path;
- every `ContextCustomizer`, which includes `@MockitoBean`/`@MockitoSpyBean`/`@TestBean` overrides, `@DynamicPropertySource` methods and Boot's customizers.

If the keys are equal, the context is reused. JVM system properties and environment variables are *not* part of the key. The cache holds 32 contexts by default and evicts the least recently used one. In lab 06, seven classes and 49 tests started only three contexts.
</details>

<details><summary>Q2. Why can @MockitoBean make a test suite slow, and how do you avoid it?</summary>

Each bean override is a context customizer, so a different set of mocked beans (even a different *field name*, which acts as a fallback qualifier) is a different cache key. That means a new context, and with Testcontainers often a new container. Avoid it as follows:
- Declare shared mocks once, on a base class or in a composed annotation (`@MockitoBean(types = {...})`).
- Name mock fields consistently.
- Prefer slices that need few mocks.
- Prefer real lightweight collaborators where possible: lab 05 imports the in-memory repository instead of mocking it.
</details>

<details><summary>Q3. What replaced @MockBean in Boot 4, and how does it differ?</summary>

`@MockBean`/`@SpyBean` were removed. Spring Framework's `@MockitoBean`/`@MockitoSpyBean` replace them.
- `@MockitoBean` uses the `REPLACE_OR_CREATE` strategy: it creates the bean if none exists. Set `enforceOverride = true` to require an existing bean.
- `@MockitoSpyBean` uses `WRAP` and needs exactly one candidate bean.
- They can be used on test-class fields (including superclasses and enclosing classes of `@Nested`) or at type level with `types`. They cannot be used in `@Configuration` classes.
- A `@MockitoBean` is registered as a manual singleton, so it has **no AOP proxy**. A spy keeps the existing proxy.
- Since Framework 7, overrides also work on non-singleton beans.
</details>

<details><summary>Q4. Explain the @SpringBootTest webEnvironment modes and their transaction semantics.</summary>

- `MOCK` (default): no server, a mock servlet environment. Combine it with `@AutoConfigureMockMvc`.
- `RANDOM_PORT`: a real embedded server on a free port.
- `DEFINED_PORT`: a real server on `server.port` or 8080.
- `NONE`: no web environment.

A `@Transactional` test method rolls back at the end. With a *real* server, though, the HTTP request runs on a server thread in its own transaction, which commits and is not rolled back. So `RANDOM_PORT` tests must clean up or assert relative counts, as lab 05's `RestApiApplicationTests.totalBooks()` does.
</details>

<details><summary>Q5. What does Boot 4 change for MockMvc, TestRestTemplate and WebTestClient in @SpringBootTest?</summary>

None of them is provided automatically any more:
- Add `@AutoConfigureMockMvc` for `MockMvc`/`MockMvcTester`.
- Add `@AutoConfigureTestRestTemplate` for `TestRestTemplate`, which moved to `org.springframework.boot.resttestclient` and also needs `spring-boot-restclient`.
- Add `@AutoConfigureWebTestClient` for `WebTestClient`.

The recommended blocking client is the new Framework 7 `RestTestClient` with `@AutoConfigureRestTestClient`. It can target a live server, a controller, or the context via MockMvc. Labs 05, 07 and 10 use it with `RANDOM_PORT`.
</details>

<details><summary>Q6. How does a test slice like @WebMvcTest work internally?</summary>

It is a composed annotation with these parts:
- `@BootstrapWith(WebMvcTestContextBootstrapper)`;
- `@ExtendWith(SpringExtension)`;
- `@OverrideAutoConfiguration(enabled = false)`, which turns off all auto-configuration;
- `@TypeExcludeFilters(WebMvcTypeExcludeFilter)`, which restricts scanning to web components such as controllers, advice, `WebMvcConfigurer` and `Filter`;
- `@ImportAutoConfiguration`, which re-enables only the auto-configs listed in the module's `META-INF/spring/...AutoConfigureWebMvc.imports` and `...AutoConfigureMockMvc.imports`.

`@Service`, `@Repository` and `@Configuration` classes are not loaded. You supply them with `@Import`, fakes, or `@MockitoBean`, as labs 05 and 07 do.
</details>

<details><summary>Q7. When would you use ApplicationContextRunner instead of @SpringBootTest?</summary>

When testing auto-configuration or a small set of infrastructure beans. It creates a fresh, tiny context per `run(...)`, without component scanning and without the TestContext cache. That makes property, classpath (`FilteredClassLoader`) and user-bean variations cheap, and it gives AssertJ assertions such as `hasSingleBean`, `doesNotHaveBean` and `hasFailed`. Examples:
- Lab 03 tests every condition of its starter this way.
- Lab 12 tests `@Async` and `@Scheduled` against Boot's real `TaskExecutionAutoConfiguration`/`TaskSchedulingAutoConfiguration` in milliseconds.
</details>

<details><summary>Q8. How do you use Testcontainers with Spring Boot, and why are container beans preferred over static @Container fields?</summary>

Declare the container as a `@Bean` with `@ServiceConnection` in a top-level `@TestConfiguration`, and `@Import` it into tests. Boot then creates a `ConnectionDetails` bean that overrides `spring.datasource.*` or the equivalent properties. Container beans start before all other beans and stop after them, so they live exactly as long as the cached context.

Static `@Container` fields are stopped by the JUnit extension after the test class. The cached context outlives the container, so a later class that reuses the context hits a dead port. For a `GenericContainer`, give `@ServiceConnection(name = "redis")`, as lab 09 does.
</details>

<details><summary>Q9. What is context pausing in Spring Framework 7?</summary>

A cached context that is not in use can be paused: all `Lifecycle`/`SmartLifecycle` beans are stopped, such as schedulers and message listener containers. It is restarted when a test retrieves it again. This stops background work in idle cached contexts from interfering with other tests.
- Control it with the `spring.test.context.cache.pause` property: `always`, `on_context_switch` (the default since 7.0.3) or `never`.
- A `SmartLifecycle` bean can opt out with `isPauseable() = false`.
</details>

<details><summary>Q10. How do you test JWT-secured endpoints at different levels?</summary>

- **Slice level.** Use `@WebMvcTest` with the real `SecurityConfig` imported, plus `jwt()` or `@WithMockUser`. This tests URL and method authorization rules quickly. But `jwt()` builds a `JwtAuthenticationToken` directly, skipping the `JwtDecoder` and your `JwtAuthenticationConverter`, so you must pass authorities explicitly.
- **End-to-end level.** Use `RANDOM_PORT`, log in, and send real bearer tokens. This proves decoding and claim-to-role mapping, and that no session is created.

Also import the real secured service rather than mocking it, or the method-security proxy disappears. Lab 07 does all of this.
</details>

<details><summary>Q11. Mock vs stub vs spy vs fake, and what does "don't mock what you don't own" mean?</summary>

- A stub returns canned answers.
- A mock also verifies interactions.
- A spy wraps a real object and overrides selected calls.
- A fake is a working, simplified implementation, such as an in-memory repository.

"Don't mock what you don't own": mocking a third-party API (`RestClient`, `EntityManager`, a Redis template) encodes your *assumptions* about it, which the mock can never falsify. Wrap the library in your own adapter, mock the adapter in unit tests, and test the adapter itself against the real thing (Testcontainers, `MockRestServiceServer`, WireMock).
</details>

<details><summary>Q12. How do you test asynchronous behavior without flakiness?</summary>

- Never `Thread.sleep`. Use Awaitility: `await().atMost(Duration.ofSeconds(5)).until(condition)`. It polls and returns as soon as the condition holds (labs 09, 10, 12).
- For races, line threads up behind a start-gate `CountDownLatch` and release them together (lab 05 `IdempotencyStoreTest`, lab 06 `LockingTest`).
- For reactive code, use `StepVerifier.withVirtualTime(() -> publisher)` and `thenAwait(...)`. Create the publisher inside the supplier, and start with `expectSubscription()` before `expectNoEvent`.
- For time-based logic, inject a `Clock`.
</details>

<details><summary>Q13. Can you run Spring integration tests in parallel?</summary>

JUnit Jupiter supports opt-in in-JVM parallelism (`junit.jupiter.execution.parallel.enabled=true`, with class and method modes). Spring's docs say not to use it for tests that use `@DirtiesContext`, `@MockitoBean`/`@MockitoSpyBean`, or `@TestMethodOrder`, or that change shared state such as a database or broker. That rules out most integration suites, including labs 05, 06, 09 and 12.

The safer option is process-level parallelism: Surefire `forkCount`, or CI sharding. Each fork has its own context cache and containers. First, though, cut the number of contexts and push tests down to slices.
</details>

<details><summary>Q14. What is consumer-driven contract testing, and how does it differ from end-to-end testing?</summary>

The consumer states the interactions it relies on. The provider runs those contracts as tests in its own pipeline. The consumer tests against stubs generated from the *verified* contract (Spring Cloud Contract stub jars, or Pact mock servers with a broker and `can-i-deploy`). This catches breaking API changes, such as a renamed field, before deployment, without standing up both services.

End-to-end tests check that deployed services work together, but they are slow, flaky, and hard to debug. Contracts replace most of them. A thin post-deploy smoke test stays.
</details>

<details><summary>Q15. Coverage is 90%. Is the suite good? How would you know?</summary>

Not necessarily. Coverage shows which lines *executed*, not which behaviors are *asserted*. A test with no assertions still yields coverage. Mutation testing (PIT) seeds small bugs and checks whether any test fails. Surviving mutants show code that is covered but not tested.

Better signals are:
- the mutation score on core logic;
- diff coverage on pull requests;
- the flaky-test rate;
- how often production bugs had no failing test.
</details>



## 13 — Reactive: WebFlux & R2DBC

_From [notes/13-reactive/webflux.md](13-reactive/webflux.md)_


<details><summary>Q1. What are the four Reactive Streams interfaces, and how does backpressure work?</summary>

`Publisher` (`subscribe`), `Subscriber` (`onSubscribe`, `onNext`, `onError`, `onComplete`), `Subscription` (`request(n)`, `cancel()`), and `Processor` (both a Subscriber and a Publisher). After subscribing, the subscriber receives a `Subscription` and must call `request(n)` before any data flows. The publisher may emit at most the total requested. That demand signal flowing upstream is backpressure: a slow consumer simply requests less. `request(Long.MAX_VALUE)` means unbounded. Signals are serial, `null` is forbidden, and `onError`/`onComplete` are terminal. Java 9 copied the interfaces into `java.util.concurrent.Flow`.
</details>

<details><summary>Q2. Mono vs Flux, and what is the difference between assembly time and subscription time?</summary>

`Mono` emits 0 or 1 item, `Flux` emits 0..N, and both then complete or error. Assembly time is when you chain operators: you build an immutable description, and nothing executes. Subscription time is when `subscribe()` is called: the subscription travels to the source, demand travels up, and data travels down. Consequences: `Mono.fromCallable` runs per subscriber, `Mono.just(expensive())` runs `expensive()` at assembly, and `switchIfEmpty(fallback())` builds its fallback eagerly unless wrapped in `Mono.defer`. The lab proves all three.
</details>

<details><summary>Q3. flatMap vs concatMap vs flatMapSequential?</summary>

`flatMap` subscribes to inner publishers eagerly (default concurrency 256) and emits in completion order: fastest throughput, no ordering. `concatMap` subscribes to one inner at a time and waits for it to complete: it keeps order but total time is the sum. `flatMapSequential` subscribes eagerly but buffers results and emits them in source order: concurrency plus order, at the cost of memory. In the lab, inners delayed 300/100/200 ms give `1,2,3` after 300 ms, `3,1,2` after 600 ms, and `3,1,2` after 300 ms respectively.
</details>

<details><summary>Q4. publishOn vs subscribeOn?</summary>

`subscribeOn` chooses the thread on which the subscription happens, so it moves the *source* and everything up to the first `publishOn`. Its position in the chain doesn't matter, and only the one closest to the source wins. `publishOn` switches the thread for all signals *below* it, so its position matters and you can use several. Typical use: `Mono.fromCallable(blockingCall).subscribeOn(Schedulers.boundedElastic())` to keep blocking work off the event loop.
</details>

<details><summary>Q5. Why must you never block the Netty event loop, and how do you call a blocking API from WebFlux?</summary>

Reactor Netty runs roughly one event-loop thread per core, each serving thousands of connections. Blocking one of them stalls every connection on that loop, so throughput collapses to a handful of concurrent requests. Wrap the blocking call lazily in `Mono.fromCallable(...)` and add `.subscribeOn(Schedulers.boundedElastic())`, which is a bounded pool (10 × cores) meant for blocking work. Use BlockHound in tests to detect accidental blocking. If most of your calls are blocking, WebFlux is the wrong choice: use MVC with virtual threads.
</details>

<details><summary>Q6. Cold vs hot publishers? What do share() and cache() do?</summary>

A cold publisher starts its work for each subscriber, from the beginning (HTTP calls, DB queries). A hot publisher emits regardless of subscribers, and late subscribers miss earlier data (sinks, tickers). `share()` (`publish().refCount()`) makes a cold source hot and shared: one upstream subscription while at least one subscriber exists. `cache()` makes it replay: the first subscriber triggers the work, and later subscribers get the stored signals, including errors. The lab proves that late `share()` subscribers miss elements and that `cache()` replays an error forever.
</details>

<details><summary>Q7. How do you do retries properly in Reactor?</summary>

Use `retryWhen(Retry.backoff(n, firstDelay))` with a `filter` for transient errors only (timeouts, connection errors, 5xx, 429). Put `timeout` before `retryWhen` so each attempt gets its own deadline, and map non-retryable errors with `onErrorMap` before it. Exhaustion throws `RetryExhaustedException` (check with `Exceptions.isRetryExhausted`), which you can turn into a fallback with `onErrorResume`. Remember that retry resubscribes and re-runs the whole upstream, so only retry idempotent operations. Test it with `StepVerifier.withVirtualTime` so backoff delays don't slow the build.
</details>

<details><summary>Q8. How does error handling differ between Reactor and try/catch?</summary>

An error is a terminal signal. After `onError`, the sequence is over, so `onErrorResume` *replaces* the rest of the sequence rather than continuing it. `onErrorReturn` gives a fallback value, `onErrorMap` translates the type, and `doOnError` only peeks. To skip a single bad element, recover inside the `flatMap` for that element. In WebFlux, `@ControllerAdvice` handles errors from annotated controllers only. A `WebExceptionHandler` handles everything, including functional endpoints and filters, which is why the lab uses one global `WebExceptionHandler` producing ProblemDetail.
</details>

<details><summary>Q9. Reactor Context vs ThreadLocal? How does MDC logging work in WebFlux?</summary>

A `ThreadLocal` is tied to a thread, but a reactive request hops threads and threads interleave requests, so a `ThreadLocal` is lost or leaks. The Reactor `Context` is an immutable map tied to the subscription. You write it with `contextWrite` (visible upstream of the call) and read it with `deferContextual`. To keep `ThreadLocal`-based libraries working (MDC, tracing), Micrometer's context-propagation library registers `ThreadLocalAccessor`s, and with `spring.reactor.context-propagation=auto` (`Hooks.enableAutomaticContextPropagation()`) Reactor restores those `ThreadLocal`s from the Context around every operator. The default `limited` mode restores them only in `handle` and `tap`.
</details>

<details><summary>Q10. What does WebFlux look like inside, compared with Spring MVC?</summary>

Netty receives the request. `ReactorHttpHandlerAdapter` adapts it to Spring's `HttpHandler`, and `HttpWebHandlerAdapter` creates a `ServerWebExchange`, runs the `WebExceptionHandler` and `WebFilter` chains, and calls `DispatcherHandler`. That finds a handler through `HandlerMapping`s (`RouterFunctionMapping`, `RequestMappingHandlerMapping`), invokes it through a `HandlerAdapter`, and writes the returned publisher with a `HandlerResultHandler`, using `Encoder`/`Decoder` codecs. MVC has the same shape with `DispatcherServlet`, servlet filters, and blocking `HttpMessageConverter`s, and its per-request state lives in `ThreadLocal`s.
</details>

<details><summary>Q11. What can't R2DBC do that JPA can? How do transactions work?</summary>

No persistence context, no dirty checking, no lazy loading, no relationships or cascades, no schema generation, and no `Page` with an automatic COUNT. You save explicitly and compose related queries yourself. Transactions use `R2dbcTransactionManager`, either declaratively (`@Transactional` on a method returning the publisher) or programmatically with `TransactionalOperator.transactional(flux)`. The transaction is bound to the Reactor Context, not a `ThreadLocal`, and it rolls back on error *or cancellation*. Flyway still needs a JDBC driver.
</details>

<details><summary>Q12. WebClient vs RestClient: which do you choose in 2026?</summary>

In a Spring MVC app, especially with virtual threads, use `RestClient`: synchronous, simple, and it scales because blocking a virtual thread is cheap. Use `WebClient` in WebFlux apps, for streaming responses (`bodyToFlux`, SSE), and when you need reactive composition with cancellation across many concurrent calls. Both back `@HttpExchange` interfaces. In Boot 4 they come from separate starters (`spring-boot-starter-webclient`, `spring-boot-starter-restclient`).
</details>

<details><summary>Q13. When should you NOT use reactive?</summary>

For typical CRUD services on JDBC/JPA, when the team isn't fluent in Reactor, for CPU-bound work, or when key dependencies are blocking. Java 21 virtual threads give blocking code the I/O scalability that was reactive's main selling point, while keeping simple stack traces, `ThreadLocal`s, JPA, and the debugger. Reactive still wins for long-lived streaming (SSE/WebSocket), gateways and proxies, and complex fan-out with cancellation. The debugging and onboarding costs are real and continuous.
</details>

<details><summary>Q14. How do you handle a slow SSE client when broadcasting to thousands?</summary>

Use a hot source that doesn't let one subscriber slow the others: `Sinks.many().multicast().directBestEffort()` drops elements only for subscribers without demand, whereas `share()`/`publish()` paces everyone to the slowest. Give each client its own bounded strategy: `onBackpressureLatest()` for prices, or `onBackpressureBuffer(n, DROP_OLDEST)`. Add heartbeats to keep proxies from closing the connection, send event ids so clients can resume with `Last-Event-ID`, and broadcast the feed to every node, because each node only owns its own connections.
</details>

<details><summary>Q15. How do you test reactive code?</summary>

Use `StepVerifier` from reactor-test: `expectNext`, `expectError`, `verifyComplete`, `thenRequest` for backpressure, `thenCancel` for infinite streams, and `withVirtualTime` with `thenAwait` for delays and retries without waiting. Use `TestPublisher` to emit on demand. For HTTP, use `@WebFluxTest` with `WebTestClient` (slice) and `@SpringBootTest(RANDOM_PORT)` with `@AutoConfigureWebTestClient` (Boot 4) for the full stack. Use Testcontainers with `@ServiceConnection` for R2DBC (`testcontainers-r2dbc` provides the R2DBC connection details). Test SSE by taking `returnResult(...).getResponseBody()` into a `StepVerifier` and cancelling.
</details>



## 11 Messaging with Kafka

_From [notes/11-messaging-kafka/kafka.md](11-messaging-kafka/kafka.md)_


<details><summary>Q1. How does Kafka differ from a traditional message queue like RabbitMQ?</summary>

Kafka is a **distributed, partitioned, replicated log**. Records are appended and kept for a retention period (or compacted by key), whatever has been read. Consumers *pull* and track their own **offsets**, so any number of consumer groups read the same data independently and can **replay** it. RabbitMQ is a **smart broker**: exchanges route messages to queues, the broker pushes them to consumers, and it deletes a message once it is acked. Rabbit offers per-message features (priorities, TTL, delayed delivery, rich routing). Kafka offers throughput, retention, replay, and per-partition ordering at scale. Choose Kafka for event streaming, CDC, and fan-out to many consumers. Choose RabbitMQ (or SQS) for task queues and complex routing.
</details>

<details><summary>Q2. What is a partition, and how does Kafka decide which partition a record goes to?</summary>

A partition is an ordered, append-only log, and the unit of parallelism, ordering, and replication. With a key, the default partitioner computes `toPositive(murmur2(keyBytes)) % numPartitions`, so the same key always goes to the same partition while the partition count is unchanged. The lab test recomputes this and matches the broker's answer. With a null key, Kafka 3.3+ uses a sticky partitioner: it fills a batch for one partition, then switches. Adding partitions changes the modulus and remaps keys, which breaks per-key ordering across the change.
</details>

<details><summary>Q3. Explain the ISR, the high watermark and `min.insync.replicas`.</summary>

Each partition has a leader and followers. The **ISR** is the set of replicas caught up with the leader (within `replica.lag.time.max.ms`). The **high watermark** is the highest offset replicated to all ISR members, and consumers can only read up to it. With `acks=all`, the leader acknowledges a write only once the whole ISR has it, **and** the ISR has at least `min.insync.replicas` members. Otherwise the producer gets `NotEnoughReplicasException`. RF=3 with `min.insync.replicas=2` survives one broker failure with no data loss and no write outage. Leaders are only elected from the ISR unless `unclean.leader.election.enable=true`, which trades durability for availability.
</details>

<details><summary>Q4. What does `enable.idempotence` do, and what does it not do?</summary>

The broker assigns the producer a **PID** and an epoch, and every batch carries a per-partition **sequence number**. If a retry resends a batch whose ack was lost, the leader sees the duplicate sequence and drops it. A gap is rejected, which keeps ordering intact even with up to 5 in-flight requests. It has been on by default since Kafka 3.0, together with `acks=all`. It does **not** dedupe across producer restarts (a new PID), across two calls to `send()` by your code, or anything on the consumer side. End-to-end deduplication still needs transactions (Kafka to Kafka) or idempotent consumers.
</details>

<details><summary>Q5. At-most-once, at-least-once, exactly-once: how do you get each, and what does Kafka's EOS really cover?</summary>

**At-most-once**: commit the offset before processing, so a crash loses the record. **At-least-once**: process, then commit, so a crash reprocesses it. This is Spring's default (offsets are committed after the listener returns, per the `AckMode`). **Exactly-once (EOS)**: a transactional producer writes its outputs and `sendOffsetsToTransaction` in one Kafka transaction, and downstream readers use `read_committed`. EOS covers **only the Kafka-to-Kafka loop**. A database write, an HTTP call, or an email inside the listener is not rolled back when the Kafka transaction aborts. For external side effects you use at-least-once plus **idempotent consumers** (a processed-id table in the same DB transaction, or upserts), which gives "effectively once".
</details>

<details><summary>Q6. What is the transactional outbox pattern, and why not just call `kafkaTemplate.send()` after saving?</summary>

Saving and sending is a **dual write** across two systems with no shared transaction. A crash after the commit loses the event, and a send before a failed commit publishes a phantom event. The outbox writes the event into an `outbox` table **in the same local DB transaction** as the business change, so both commit or neither does. A relay (a `@Scheduled` poller with `FOR UPDATE SKIP LOCKED`, or Debezium CDC reading the WAL) publishes the rows to Kafka and marks them sent. Delivery is at-least-once, so consumers must be idempotent. The lab proves that a rollback leaves neither row, and that the relay plus the consumer reserve stock end to end.
</details>

<details><summary>Q7. How do you make a Kafka consumer idempotent?</summary>

Give every event a unique id (`eventId`). In the **same database transaction** as the side effect, insert that id into a `processed_message` table with a primary key. If the insert affects 0 rows (`INSERT ... ON CONFLICT DO NOTHING` in Postgres), it is a duplicate, so skip it. If the side effect fails, the transaction rolls back the marker too, and the redelivery is processed again. Don't catch a duplicate-key exception inside the transaction, because Postgres aborts the whole transaction after a failed statement. Alternatives are natural idempotency (upsert by business key, or `UPDATE ... WHERE version = n`) or storing consumer offsets in the same DB.
</details>

<details><summary>Q8. What happens during a consumer group rebalance, and how do you minimize its impact?</summary>

When members join or leave, or partitions change, the group reassigns partitions. With the classic **eager** protocol, every member revokes all its partitions and processing stops group-wide until the new assignment. **Cooperative sticky** revokes only the partitions that move, over two rounds. **KIP-848** (`group.protocol=consumer`, GA in Kafka 4.0, opt-in) moves assignment to the broker and makes it fully incremental. Other ways to reduce impact: **static membership** (`group.instance.id`) so restarts within `session.timeout.ms` don't rebalance, keeping processing well within `max.poll.interval.ms`, and committing offsets in `onPartitionsRevoked`. Spring's container already commits pending offsets on revocation (unverified).
</details>

<details><summary>Q9. `session.timeout.ms` vs `max.poll.interval.ms`: what is the difference?</summary>

`session.timeout.ms` (45 s) is liveness. A background heartbeat thread proves the **process** is alive. `max.poll.interval.ms` (5 min) is progress. If your code does not call `poll()` again within it, because processing one batch of up to `max.poll.records` (500) took too long, the consumer proactively leaves the group even though it is alive. Its partitions go to someone else, its commit fails with `CommitFailedException`, and the records are processed twice. Fix it by processing faster, lowering `max.poll.records`, or raising the interval. Also keep `DefaultErrorHandler` back-offs short, because they block the poll loop.
</details>

<details><summary>Q10. `DefaultErrorHandler` vs `@RetryableTopic`: when do you use which?</summary>

`DefaultErrorHandler` retries **in place (blocking)**. It re-seeks the failed record, sleeps per the `BackOff`, and after the retries calls a recoverer such as `DeadLetterPublishingRecoverer`, which publishes to `<topic>-dlt` on the same partition. Not-retryable exceptions (deserialization errors, your business rejections) go straight to the recoverer. It **preserves per-key order**, but it blocks the partition, so back-offs must be short. `@RetryableTopic` retries **non-blocking**: the failed record is re-published to retry topics with growing delays while the main partition keeps flowing, and ends in a `-dlt` topic handled by a `@DltHandler`. It suits independent events with slow dependencies, but **breaks ordering**. The lab uses `DefaultErrorHandler`, because order events must stay ordered.
</details>

<details><summary>Q11. What does `ConcurrentKafkaListenerContainerFactory` create, and what does `concurrency` actually mean?</summary>

For each `@KafkaListener`, the factory creates a `ConcurrentMessageListenerContainer`. That container creates `concurrency` child `KafkaMessageListenerContainer`s. Each child owns one `KafkaConsumer` and one thread, which polls and invokes your listener. All children are in the same group, so Kafka splits the partitions between them. Concurrency is capped by the partition count: with 3 partitions and concurrency 5, two children are idle. The lab test shows 3 children with exactly 1 partition each. Containers are registered in `KafkaListenerEndpointRegistry` (a `SmartLifecycle`), which starts them late in the context refresh and lets you pause, resume, or stop them by id.
</details>

<details><summary>Q12. How does Kafka guarantee ordering, and how can you accidentally lose it?</summary>

Ordering is guaranteed **only within a partition**. Use the entity id as the key, so all of its events go to one partition and are consumed by one thread. You lose ordering by: using no key or a random key, adding partitions (keys remap), disabling idempotence with retries and more than one in-flight request, using `@RetryableTopic` (a retried record is overtaken), handing records to a thread pool inside the listener, or redriving old DLT records after newer events were processed. Consumers that must be robust anyway should check an aggregate version and drop stale events.
</details>

<details><summary>Q13. What is log compaction, and when would you use a compacted topic?</summary>

With `cleanup.policy=compact`, Kafka keeps at least the **latest value per key** and a background cleaner removes older ones. A null value is a **tombstone** that deletes the key after `delete.retention.ms`. Offsets are preserved (with gaps). Use it for "current state" topics: entity snapshots, configuration, a KTable changelog, `__consumer_offsets` itself, or a lookup table that new services bootstrap from by reading from the beginning. Pair it with an infinite-retention event topic when you need the full history (event sourcing or audit).
</details>

<details><summary>Q14. How do you evolve event schemas without breaking consumers?</summary>

Use a schema format with evolution rules (Avro or Protobuf) and a **Schema Registry**. The producer registers the schema and sends a schema id with each record. The consumer fetches the writer's schema and resolves it against its own. The registry enforces a **compatibility mode** per subject. `BACKWARD` (the default) means new readers can read old data: delete fields or add fields with defaults, and upgrade consumers first. `FORWARD` means old readers can read new data: add fields, and upgrade producers first. `FULL` means both. `*_TRANSITIVE` checks all past versions. With JSON (like the lab), you rely on discipline: only add optional fields, never rename or change types, use tolerant readers (ignore unknown properties), and run contract tests.
</details>

<details><summary>Q15. What changed for Kafka in Spring Boot 4 / Spring Kafka 4?</summary>

Spring Kafka 4.x is built on the Apache Kafka 4 clients (Boot 4.1.2 ships Spring Kafka 4.1.2 and kafka-clients 4.2.2). **ZooKeeper support is removed** (`EmbeddedKafkaZKBroker` is gone, KRaft only). **Jackson 3** classes replace the Jackson 2 ones, which are deprecated: `JacksonJsonSerializer`/`Deserializer`, `JacksonJsonMessageConverter`, and `JsonKafkaHeaderMapper`. **Spring Retry was replaced by Framework 7's core retry**, so `@RetryableTopic` uses `@BackOff` instead of `@Backoff`, and Boot's `spring.kafka.retry.topic.backoff.random` became `...jitter`. The **KIP-848** rebalance protocol and **share consumers** (Kafka Queues) are supported. 4.1 adds a per-listener `ackMode`. Boot moved Kafka into the `spring-boot-kafka` module with `spring-boot-starter-kafka` and `-kafka-test`. Testcontainers 2 uses `testcontainers-kafka` with `org.testcontainers.kafka.KafkaContainer` (for example on `apache/kafka-native`) and `@ServiceConnection`.
</details>



## 14 Microservices & Spring Cloud

_From [notes/14-microservices/spring-cloud.md](14-microservices/spring-cloud.md)_


<details><summary>Q1. When would you choose microservices over a modular monolith, and what does the choice cost?</summary>

Choose microservices when you have **independent teams that need independent deploys**, parts with **very different scaling or availability needs**, or **isolation requirements** (PCI scope, fault isolation). The cost is the distributed systems tax:
- network failures and latency on every call (the eight fallacies);
- no ACID across services (sagas, outbox, eventual consistency);
- API versioning;
- per-service CI/CD, tracing, config and on-call.

For one team or an unclear domain, a modular monolith (Spring Modulith) gives enforced boundaries and in-process events at a fraction of the cost. It also keeps the option to extract later. Split along DDD bounded contexts, with a database per service. If two services always deploy together, you built a distributed monolith.
</details>

<details><summary>Q2. Explain the circuit breaker states and the settings that drive the transitions.</summary>

- **CLOSED:** calls pass, and their outcomes are recorded in a sliding window (count-based or time-based).
- **CLOSED to OPEN:** once at least `minimumNumberOfCalls` calls are recorded, the breaker opens when the failure rate reaches `failureRateThreshold` or the slow-call rate reaches `slowCallRateThreshold` (calls slower than `slowCallDurationThreshold`).
- **OPEN:** calls fail fast with `CallNotPermittedException` and go to the fallback.
- **OPEN to HALF_OPEN:** after `waitDurationInOpenState`.
- **HALF_OPEN:** allows `permittedNumberOfCallsInHalfOpenState` trial calls. It closes if they are healthy and opens again if not.
- **Manual states:** DISABLED, FORCED_OPEN and METRICS_ONLY.

The Resilience4j defaults are 100 calls in the window, 100 minimum calls, a 50% failure rate, 60 s open, and 10 half-open calls. The minimum of 100 is why breakers "never trip" in tests.
</details>

<details><summary>Q3. In which order do Retry, CircuitBreaker, RateLimiter, TimeLimiter and Bulkhead apply, and why does it matter?</summary>

With Resilience4j's Spring annotations, the default is `Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))`:
- **Retry is outermost**, so each attempt passes through the breaker and is counted. Once the breaker opens, the retries see `CallNotPermittedException`. Configure Retry to ignore it, or it wastes attempts.
- **The bulkhead is innermost**, so rejections count as breaker failures.

If you put the breaker *outside* the retry, it would count one failure per *logical* call instead of per attempt. Change the order with the `*AspectOrder` properties or the `Decorators` builder. Spring Cloud CircuitBreaker's factory itself composes CircuitBreaker + TimeLimiter (+ Bulkhead if present).
</details>

<details><summary>Q4. Spring Cloud Gateway: what are routes, predicates and filters, and how do you choose between the WebFlux and Web MVC servers?</summary>

- A **route** is an id, a destination URI, predicates and filters.
- **Predicates** (Path, Method, Host, Header, Weight and so on) decide whether the route matches. The first matching route wins.
- **Filters** modify the request or response: StripPrefix, headers, CircuitBreaker, Retry, TokenRelay, rate limiting.

**WebFlux** (`spring-cloud-starter-gateway-server-webflux`, `spring.cloud.gateway.server.webflux.*`) runs on Netty and is non-blocking. It suits very high concurrency, streaming and WebSockets, and it has the Redis rate limiter.

**Web MVC** (`...-server-webmvc`, `...server.webmvc.*`) is functional routing on the Servlet stack. It is simpler to debug, scales well with virtual threads, and uses Bucket4j for rate limiting.

Both were renamed in 2025.0, and the old starters don't exist for Boot 4. Initializr's `cloud-gateway` id is now the Web MVC variant.
</details>

<details><summary>Q5. How does gateway rate limiting work, and how do you make it work across several gateway replicas?</summary>

It uses the token bucket algorithm. Each key (user, API key, or IP) has a bucket with capacity B that refills at R tokens per second. A request consumes tokens, and an empty bucket means 429.

To share limits across replicas, the bucket state must be central:
- **WebFlux:** the `RequestRateLimiter` filter with `RedisRateLimiter` (`replenishRate`, `burstCapacity`, `requestedTokens`). It runs an atomic Lua script in Redis and needs `spring-boot-starter-data-redis-reactive`. The default key resolver is the principal name, and a missing key is denied.
- **Web MVC:** Bucket4j with a distributed `ProxyManager` backend. The key resolver is Java-DSL only, and a missing key returns 403.
</details>

<details><summary>Q6. Eureka vs Kubernetes Services: why do most K8s shops skip Eureka?</summary>

Kubernetes already provides discovery:
- A Service gets a stable DNS name and a virtual IP.
- Its endpoints are maintained from **readiness probes**.
- kube-proxy (or a mesh) balances traffic.

Eureka would add a stateful server to operate and a 30–90 s staleness window from heartbeats and caches, and it only helps JVM clients. On K8s you call `http://payment-service` and need neither Eureka nor client-side load balancing. With HTTP service groups, a plain `http://` base URL disables LoadBalancer interception.

Eureka still fits VM or ECS fleets, and hybrid setups where some services live outside the cluster.
</details>

<details><summary>Q7. How does `@RefreshScope` work, and when don't you need it?</summary>

A refresh (`/actuator/refresh`, or `/actuator/busrefresh` through Spring Cloud Bus) makes `ContextRefresher` rebuild the Environment and publish an `EnvironmentChangeEvent` with the changed keys:
- `ConfigurationPropertiesRebinder` then re-binds `@ConfigurationProperties` beans in place. That works for JavaBean-style classes with setters, but **not for records**. A test showed that a record keeps its old value, because constructor binding is immutable.
- `@RefreshScope` beans are lazy scoped proxies. Refreshing clears the scope's cache, so the next call builds a new instance with the new `@Value`s and constructor-time state.

A plain `@Value` on a singleton also keeps the old value. You need `@RefreshScope` for beans that take `@Value`s or bake config into their state at construction. The same test showed that a `@RefreshScope` bean's `@Value` does update.

On Kubernetes, many teams skip hot refresh entirely and roll pods on a ConfigMap change, for reproducibility.
</details>

<details><summary>Q8. `@HttpExchange` vs OpenFeign: what would you use on Boot 4, and how do you add load balancing and fallbacks?</summary>

Use `@HttpExchange`. OpenFeign has been feature-complete since 2022.0, and its docs recommend migrating to Spring HTTP Service Clients. `@HttpExchange` interfaces are backed by `RestClient` or `WebClient`, so you get their observability, SSL bundles, interceptors and Boot properties. They also support reactive types.

Framework 7's `@ImportHttpServices` registers the clients in **groups**, and Boot 4 configures each group with `spring.http.serviceclient.<group>.base-url`, timeouts and so on. Spring Cloud 2025.1 adds:
- **Load balancing:** the group's base URL is unset (it becomes `http://<group>`) or uses `lb://`.
- **Circuit breaking:** `@HttpServiceFallback(value = Fallback.class, group = "...")`. The fallback methods match by name and parameters, optionally with a leading `Throwable`.
</details>

<details><summary>Q9. How do you keep data consistent across services without distributed transactions?</summary>

Use a **saga**, a series of local transactions with compensating actions:
- **Choreography:** services react to each other's events.
- **Orchestration:** a central coordinator sends commands.

Make it reliable with three things:
1. **A transactional outbox.** The business row and the event row commit in one local transaction, and a relay or CDC publishes the events. That gives at-least-once delivery without dual-write races.
2. **Idempotent consumers.** A processed-message-id table is updated in the same transaction as the side effect. HTTP uses `Idempotency-Key`.
3. **A UX that accepts eventual consistency.** The order stays `PENDING` until the saga completes.

2PC/XA is avoided because it locks across the network, needs every participant to support it, and makes the coordinator a single point of failure.
</details>

<details><summary>Q10. API composition or CQRS for a page that needs data from four services?</summary>

**API composition** (a BFF calls the four services in parallel and merges the results) is simple and real-time. But its availability is the product of all four, so it needs fallbacks per call, and its latency is the slowest call.

**CQRS** builds a denormalized read model from the services' events in its own store. It is fast and stays available, but it is eventually consistent and needs replay and rebuild tooling.

Use composition for detail pages that must be fresh. Use CQRS for lists, search, dashboards and cross-service filtering or sorting, which composition can't do efficiently, for example "orders over $100 whose shipment is late".
</details>

<details><summary>Q11. How do you secure service-to-service calls?</summary>

There are two identities.

**The user's identity** travels as the user's JWT:
- The gateway validates it, or converts a session to a token with `TokenRelay`.
- Each service **re-validates** the JWT as a resource server (zero trust).
- Token exchange can narrow the token's audience.

**The service's own identity** is used when there is no user, such as a job or a consumer:
- It uses an OAuth2 `client_credentials` token, attached by `OAuth2ClientHttpRequestInterceptor` on `RestClient`.
- The downstream authorizes it by scope.

Add mTLS (a mesh or SSL bundles) for workload identity and encryption in transit. Never log or propagate tokens in baggage.
</details>

<details><summary>Q12. How do you test that a change in `payment-service` won't break `order-service`?</summary>

Use consumer-driven contract tests:
1. The order team writes contracts (or Pact files) describing the requests it makes and the responses it relies on.
2. The payment build verifies them against its real controllers and publishes stubs.
3. The order build tests its `@HttpExchange` client against those stubs.

A breaking change fails the producer's build before deploy.

Note the 2025.1 change: **Spring Cloud Contract is no longer in the release-train BOM**. Initializr offers Stubborn Contract, its official continuation, which reads existing contracts unchanged. Pact is the polyglot alternative.

Complement contracts with WireMock fault-injection tests for resilience, Testcontainers integration tests per service, and only a few end-to-end journeys.
</details>

<details><summary>Q13. What is the strangler fig pattern, and what is the most common mistake?</summary>

It is an incremental migration:
1. Put a gateway in front of the monolith.
2. Build one bounded context as a new service with its own database, synced through CDC or an outbox plus an anti-corruption layer.
3. Shift that context's routes gradually, using canary weights.
4. Delete the old code, and repeat.

**The most common mistake** is letting the new service read or write the legacy database directly to save time. That keeps the coupling, and the "migration" never finishes. The second most common is extracting a context with unclear seams first. Pick a well-bounded, valuable one.
</details>

<details><summary>Q14. Framework 7 ships `@Retryable`. Does that replace Resilience4j?</summary>

No. Framework 7's `@Retryable` and `@ConcurrencyLimit` (in `org.springframework.resilience.annotation`, activated by `@EnableResilientMethods`, which Boot doesn't auto-enable) cover retries and concurrency throttling without extra dependencies.

They have **no circuit breaker, rate limiter or time limiter, and no Micrometer metrics**. For calls across a network in production, use Resilience4j (directly, or via Spring Cloud CircuitBreaker, which the Gateway filter and `@HttpServiceFallback` use). Use the core annotations for simple in-service retries, such as an optimistic-lock retry or a flaky local resource.
</details>

<details><summary>Q15. A checkout page goes down whenever the recommendation service is slow. Walk through the fix.</summary>

This is a cascading failure. Slow calls hold request threads until the pool is exhausted. The fix:
1. **A timeout** on the optional call (TimeLimiter or a read timeout, e.g. 300 ms).
2. **A bulkhead** capping concurrent recommendation calls (a semaphore with virtual threads), so it can't consume checkout's capacity.
3. **A circuit breaker** that opens on the slow-call or failure rate, so calls stop entirely during the incident.
4. **A fallback**: an empty list or cached top sellers.
5. **Alerts** on breaker state, so the degradation is visible.

Critical dependencies (pricing) get timeouts and idempotent retries, but no silent fallback. Also check that nothing retries at several layers.
</details>



## 15 Packaging & deployment

_From [notes/15-packaging-deploy/packaging-deploy.md](15-packaging-deploy/packaging-deploy.md)_


<details><summary>Q1. How does a Spring Boot fat jar run classes from jars nested inside it, when Java can't load nested jars?</summary>

The manifest's `Main-Class` is Boot's `org.springframework.boot.loader.launch.JarLauncher`, not your app; your class is in `Start-Class`. `JarLauncher` registers a `nested:` URL protocol handler, reads `BOOT-INF/classpath.idx`, and creates a `LaunchedClassLoader` (a `URLClassLoader`) over URLs like `jar:nested:/app.jar/!BOOT-INF/lib/x.jar!/`. `NestedJarFile` maps each entry of each nested jar to a byte offset in the outer file, so loading a class just seeks to the right offset and inflates that entry. Nothing is unpacked. That's why nested jars must be stored uncompressed (`STORED`). Finally it sets the thread context classloader and invokes `Start-Class.main()` reflectively. Caveat: `ClassLoader.getSystemClassLoader()` can't see nested classes.
</details>

<details><summary>Q2. What are layered jars, and how do you build an efficient Docker image from one in Boot 4?</summary>

The plugin writes `BOOT-INF/layers.idx`, which assigns jar content to layers ordered by change frequency: `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`. In a multi-stage Dockerfile you run `java -Djarmode=tools -jar app.jar extract --layers --destination extracted`, then `COPY` each layer folder separately into the runtime image, least-changing first. Docker caches layers, and a layer is reused only if all layers below it are unchanged, so a code-only change rebuilds and pushes only the small `application` layer. In Boot 4, `-Djarmode=layertools` was removed (it fails with "Unsupported jarmode"). Extracting without `--launcher` produces a plain `java -jar app.jar` + `lib/` classpath, which is also what CDS and the AOT cache need.
</details>

<details><summary>Q3. Why is setting -Xmx in a container image risky, and what do you do instead?</summary>

The kernel enforces the container's memory limit on the whole process, not on the heap. Java memory is heap + metaspace + code cache + thread stacks + direct buffers + GC overhead. A fixed `-Xmx` is decoupled from the limit: when someone lowers the pod's memory, the JVM can grow past it and gets OOM-killed (exit 137) with no `OutOfMemoryError` or heap dump. Since JDK 10 the JVM is container-aware (`UseContainerSupport`), so use `-XX:MaxRAMPercentage=70–75`, which scales with whatever limit the platform sets. The default is only 25% (in a 512 MB container that's a 128 MB heap). Leave ~25% headroom for non-heap memory, and set the memory request equal to the limit.
</details>

<details><summary>Q4. Explain CDS, the Java 25 AOT cache, and Spring AOT. Which problem does each solve?</summary>

CDS (Class Data Sharing) archives parsed class metadata to a `.jsa` file that the JVM memory-maps on startup, skipping class parsing and verification. On Java 21 you create it with `-XX:ArchiveClassesAtExit` during a training run (`-Dspring.context.exit=onRefresh`) and use it with `-XX:SharedArchiveFile`. The AOT cache (Project Leyden; JEP 483 in JDK 24, JEP 514/515 in JDK 25) goes further: classes are stored already loaded and linked, plus method profiles so the JIT warms up sooner. Use `-XX:AOTCacheOutput` / `-XX:AOTCache`. Both work only on the extracted jar with the same classpath and JVM build. Spring AOT is different: it runs at build time (`process-aot`) and generates Java source for bean definitions, so Spring doesn't scan, evaluate conditions or reflect at runtime. On the JVM it's enabled with `-Dspring.aot.enabled=true`, and it's mandatory for native image.
</details>

<details><summary>Q5. What are the trade-offs of GraalVM native image for a Spring Boot service, and when would you avoid it?</summary>

Pros: startup in tens of milliseconds, much lower memory, a single binary, a smaller attack surface. Cons: a closed world. Reflection, resources, proxies and serialization must be declared via hints; the classpath and the bean graph are fixed at build time (`@Profile` and `@ConditionalOnProperty` are evaluated during the build); there's no JIT, so peak throughput is usually lower; builds take minutes and GBs of RAM; Java agents and some libraries don't work; debugging tools are weaker. Boot 4 needs GraalVM 25+. Avoid it for long-running, throughput-bound services, apps that rely on dynamic features or agents, and teams without native test coverage. Use it for scale-to-zero functions, CLIs and memory-bound fleets.
</details>

<details><summary>Q6. How do you tell Spring AOT and GraalVM about reflection your code does?</summary>

Implement `RuntimeHintsRegistrar` and register it with `@ImportRuntimeHints` on a configuration class. In `registerHints`, call `hints.reflection().registerType(X.class)` (in Framework 7, a type hint implies member introspection; most `MemberCategory` values are deprecated), `hints.resources().registerPattern("rules/**/*.json")` (glob syntax since Framework 7), and the proxy and serialization registries as needed. For DTOs you bind manually, use `@RegisterReflectionForBinding`. Spring AOT writes all hints into `META-INF/native-image/<group>/<artifact>/reachability-metadata.json`. Test hints with `RuntimeHintsPredicates` in a plain unit test, and run `-PnativeTest` in CI.
</details>

<details><summary>Q7. Buildpacks vs a hand-written Dockerfile?</summary>

`./mvnw spring-boot:build-image` uses Cloud Native Buildpacks (default builder `paketobuildpacks/builder-noble-java-tiny`) to produce an OCI image with no Dockerfile. It installs the right JRE, uses `layers.idx`, runs as non-root, adds a memory calculator and an SBOM, and supports `pack rebase` to patch the OS layer without a rebuild. You configure it with `BP_*` env vars (e.g. `BP_JVM_AOTCACHE_ENABLED=true` on Java 25). A Dockerfile gives full control (base image, OS packages, custom layout, CDS on Java 21) at the cost of maintaining it, patching the base image and doing security hardening yourself. Choose buildpacks for standard services and platform consistency, and a Dockerfile when you need control.
</details>

<details><summary>Q8. Walk through what happens when Kubernetes terminates a Spring Boot pod, and how you avoid dropped requests.</summary>

Kubernetes marks the pod Terminating and, in parallel, removes it from the Service endpoints (proxies learn this asynchronously) and runs the `preStop` hook, then sends `SIGTERM`. Boot then publishes `ReadinessState.REFUSING_TRAFFIC`, the web server stops accepting new connections ("Commencing graceful shutdown"), in-flight requests drain for up to `spring.lifecycle.timeout-per-shutdown-phase` (30 s default), and the context closes. If the process is still alive after `terminationGracePeriodSeconds` (30 s default, counted from the start of `preStop`), it's `SIGKILL`ed. To avoid dropped requests: add a `preStop` sleep (5–10 s) so proxies converge before the app stops accepting; use exec-form `ENTRYPOINT` so Java receives `SIGTERM`; and set the grace period ≥ preStop + drain timeout + margin.
</details>

<details><summary>Q9. Liveness vs readiness vs startup probe: what goes in each?</summary>

Liveness answers "is this process broken beyond repair?" Failing it restarts the container, so it should reflect only internal state (`livenessState`), never the DB or downstream services, or one dependency blip restarts the whole fleet. Readiness answers "should this pod get traffic right now?" Failing it removes the pod from Service endpoints without a restart; it covers warm-up, shutdown (Boot flips it on `SIGTERM`) and per-pod issues. A startup probe protects slow-starting JVMs: liveness and readiness don't run until it passes, so give it a generous failure budget. In Boot 4 the `/actuator/health/liveness` and `/readiness` groups are on by default, and `add-additional-paths=true` serves `/livez` and `/readyz` on the main port, so probes test the same connector users hit.
</details>

<details><summary>Q10. How would you set up CI for a monorepo with several Spring Boot services?</summary>

One workflow whose first job computes changed projects with a path filter (e.g. `dorny/paths-filter`), then a matrix job per changed project: `actions/setup-java` with `cache: maven` and `cache-dependency-path` on that project's pom, `./mvnw -B verify` (Testcontainers works because Docker is preinstalled on `ubuntu-latest` runners), then on `main` a buildx build with GHA layer caching that pushes an image tagged with the commit SHA. Include shared files (parent poms, shared libraries, the workflow itself) in every relevant filter. Use an always-running summary job as the required check, because a workflow skipped by `on.push.paths` leaves required checks pending. CD (Argo CD/Flux) then promotes the same SHA through environments.
</details>

<details><summary>Q11. Rolling update vs blue/green vs canary: when would you choose each?</summary>

Rolling (the Deployment default) replaces pods gradually, controlled by `maxSurge`/`maxUnavailable` and gated by readiness. It's free and fine for most services, but a bad version reaches everyone as the rollout proceeds, and rollback is another rollout. Blue/green runs two full environments and flips the Service selector or LB at once. Rollback is instant and you can smoke-test green first, but it needs 2× capacity and everyone switches at once. Canary sends a small percentage of real traffic to the new version and promotes or aborts automatically based on metrics (Argo Rollouts, Flagger, a mesh). It has the smallest blast radius but needs traffic-splitting infra and good SLO metrics. All three require backward-compatible DB changes, because versions overlap.
</details>

<details><summary>Q12. What does spring-boot-docker-compose do, and how is it different from Testcontainers?</summary>

With `spring-boot-docker-compose` on the classpath (marked optional, so Boot 4 keeps it out of the uber jar), `DockerComposeLifecycleManager` runs `docker compose up` on startup for the project's `compose.yaml`, waits for each container's mapped port to accept TCP, and registers `ConnectionDetails` beans matched on image names, so `DataSource` and Redis wire themselves without URLs. It stops containers on exit (`lifecycle-management`), and it's skipped in tests by default. Testcontainers manages containers from Java code: `@ServiceConnection` beans in tests, or at dev time via `SpringApplication.from(App::main).with(Containers.class)` and `spring-boot:test-run`. Compose suits teams with a shared `compose.yaml`; Testcontainers reuses the same container definitions as the integration tests.
</details>

<details><summary>Q13. How do the twelve-factor principles map onto a Boot + Kubernetes deployment?</summary>

Config (III) lives in the environment: ConfigMaps/Secrets become env vars or `configtree:` files bound to `@ConfigurationProperties`. Build/release/run (V): CI builds one SHA-tagged image, and releases combine it with environment config. Processes (VI): stateless pods, no local session or files. Port binding (VII): embedded Tomcat behind a Service. Concurrency (VIII): HPA adds pods. Disposability (IX): fast startup (CDS/AOT cache/native) and graceful shutdown with preStop. Dev/prod parity (X): the same image everywhere, plus Compose/Testcontainers with real Postgres. Logs (XI): JSON to stdout. Admin processes (XII): Flyway or batch jobs as Kubernetes Jobs with the same image.
</details>

<details><summary>Q14. Your Spring service was fine on a VM but is slow to start and gets killed on Kubernetes. What do you check?</summary>

1. The CPU limit: startup is CPU-bound (JIT, class loading), and a 500m limit throttles it heavily. Raise it or remove the CPU limit and keep a request.
2. Probes: without a `startupProbe`, the liveness probe kills the JVM before it's ready (`CrashLoopBackOff` with liveness failures in events).
3. Memory: a heap from a fixed `-Xmx`, or the 25% default, versus the limit. Look for exit code 137 in `kubectl describe`.
4. Below 2 CPUs or ~1.8 GB, ergonomics picks SerialGC.
5. Startup acceleration: extracted jar + CDS (21) or AOT cache (25), and Spring AOT.
6. Beans doing remote I/O in constructors.

Check `/actuator/startup` with `BufferingApplicationStartup` to find slow beans.
</details>


