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


