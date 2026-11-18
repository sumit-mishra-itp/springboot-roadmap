# Lab 03/04: custom starter, auto-configuration, profiles & externalized config

Notes: [03 Boot internals: auto-configuration](../../notes/03-boot-internals/auto-configuration.md) and [04 Configuration & profiles](../../notes/04-configuration/externalized-config.md).

This is a multi-module Maven build with one parent pom and one `mvnw`:

| Module | What it is |
|---|---|
| `greeting-spring-boot-autoconfigure` | The "library" (`GreetingService`) plus `GreetingAutoConfiguration` and the validated `GreetingProperties` record. It registers the auto-config in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and generates metadata with `spring-boot-configuration-processor` and `spring-boot-autoconfigure-processor` |
| `greeting-spring-boot-starter` | A pom-only starter: autoconfigure module + `spring-boot-starter` + `spring-boot-starter-validation` |
| `demo-app` | Uses the starter. Overrides the bean under `@Profile("loud")`, has `application-dev.yml`, a multi-document `application.yml` (prod), a profile group, `@ConfigurationProperties` vs `@Value`, `${...:default}`, `${random.uuid}` |

## Run

```bash
./mvnw verify                                   # builds all three modules, runs 36 tests (11 + 25)
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar                              # Hello, platform-team!
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo # HEY PLATFORM-TEAM, YOU ARE ON DEV
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar --debug                      # prints the condition evaluation report
java -jar demo-app/target/demo-app-0.0.1-SNAPSHOT.jar --greeting.template=oops     # FailureAnalyzer: "Reason: must contain %s"
```

`spring-boot:run` from the root would try to run every module. Use the jar, or run `./mvnw install` once and then `./mvnw -pl demo-app spring-boot:run`.

## What each test proves

**greeting-spring-boot-autoconfigure** (`ApplicationContextRunner`, no app needed)

| Test | Proves |
|---|---|
| `registersDefaultService_whenNothingIsConfigured` | Defaults come from `@DefaultValue`. `matchIfMissing = true` turns it on without any property |
| `bindsTemplate_fromProperty` | `greeting.template` binds into the record |
| `backsOff_whenUserDefinesGreetingService` | `@ConditionalOnMissingBean`: the user's bean wins and there is exactly one bean |
| `backsOff_whenLibraryClassIsMissing` | `@ConditionalOnClass` with `FilteredClassLoader`. The whole class is skipped, including the properties bean |
| `backsOff_whenDisabledByProperty` | `@ConditionalOnProperty(greeting.enabled=false)` |
| `failsFast_whenTemplateIsInvalid` | `@Validated` record: startup fails with `BindValidationException` |
| `conditionEvaluationReport_recordsWhyAutoConfigurationDidNotMatch` | The `--debug` report is a real object (`ConditionEvaluationReport`) |
| `conditionalOnBean_inUserConfiguration_cannotSeeAutoConfiguredBeans` | User config is parsed before auto-config, so `@ConditionalOnBean` there misses auto-configured beans |
| `StarterPackagingTest` (3 tests) | `ImportCandidates` finds the imports file, and both metadata files are generated (record `@param` becomes the description) |

**demo-app**

| Test | Proves |
|---|---|
| `ProfilesTest` | `default` profile, `application-dev.yml` override, `@Profile("loud")` bean override, the `demo` profile group, and the `prod` document in a multi-document `application.yml` |
| `DevProfileSpringBootTest` | `@ActiveProfiles("dev")` in a `@SpringBootTest` |
| `PropertyPrecedenceTest` | application.yml < system property < command-line argument, and a command-line argument beats a profile-specific file |
| `TestPropertyPrecedenceTest` | `@SpringBootTest(properties)` beats application.yml, and `@TestPropertySource` beats `@SpringBootTest(properties)` |
| `ConfigBindingTest` | `Duration`/`DataSize` conversion, `${APP_OWNER:platform-team}`, `${random.uuid}`, SpEL `#{...}`, nested `${a:${b}}` defaults and backslash `\${...}` escaping, and that `@Value` does not relax a camelCase key against a kebab YAML key |
| `RelaxedBindingTest` | camelCase and underscore keys bind to a kebab property; `APP_SUPPORT_ONCALLEMAIL` binds from the system environment; nested `@Valid` validation |
| `ConfigImportTest` | `configtree:` (Kubernetes mounted files), a `.env` file through the `[.properties]` extension hint, and `optional:` |
| `StartupOrderTest` | The exact order of `BootstrapRegistryInitializer`, `EnvironmentPostProcessor` (Boot 4 package), `ApplicationContextInitializer`, the `SpringApplication` events, and `ApplicationRunner` |
