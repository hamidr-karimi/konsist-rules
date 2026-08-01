# Konsist Architecture Rules

[![](https://jitpack.io/v/hamidr-karimi/konsist-rules.svg)](https://jitpack.io/#hamidr-karimi/konsist-rules)

A shared **Konsist architecture-rule library** published as a JAR (`com.github.hamidr-karimi:konsist-rules`). Consuming Kotlin/Spring projects add it as a test dependency and the rules run as part of their JUnit 5 test suite — no Gradle plugin or annotation processor required.

## Quick Start

### 1. Add the dependency

**build.gradle.kts (Gradle — JitPack)**

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation("com.github.hamidr-karimi:konsist-rules:1.3")
}
```

The library brings Konsist and JUnit Jupiter transitively. Spring annotations (`@Service`, `@RestController`, `@Repository`, `@Autowired`) are referenced at compile time but **not** included — your project supplies its own Spring version.

### 2. Run the rules

```bash
./gradlew test
```

The custom `KonsistTestEngine` is discovered automatically via Java's `ServiceLoader` mechanism. No registration needed.

### 3. Filter by tag

Run specific rule categories with JUnit Platform tags:

```bash
./gradlew test --tests "*" -DincludeTags="konsist-naming"
./gradlew test --tests "*" -DincludeTags="konsist-layer-dependency"
```

## Available Rules

| Tag                        | Class                        | What it enforces                                                                                                                                                                                                                                                    |
|----------------------------|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `konsist-domain-modeling`  | `DomainModelingRulesTest`    | Shared `Id` value types must follow canonical shape (`@JvmInline`, `value class` wrapping `UUID`, private constructor, factory methods, KDoc); functions must not use `Pair` or `Triple` in parameters or return types                                              |
| `konsist-general`          | `GeneralConventionRulesTest` | No `println`/`print` in production code; no `!!` not-null assertion operator                                                                                                                                                                                        |
| `konsist-immutability`     | `ImmutabilityRulesTest`      | All class/object/companion/top-level properties must be immutable `val` (no `var`, no `lateinit`, no mutable collection types on non-private properties); public functions must not expose mutable collection return types                                          |
| `konsist-layer-dependency` | `LayerDependencyRulesTest`   | Controllers must not depend on repositories (constructor or field injection, detected by name or `@Repository` annotation); domain layer must not depend on other layers; no `@Autowired` field injection; no redundant `@Autowired` on single primary constructors |
| `konsist-naming`           | `NamingConventionRulesTest`  | `@Service` classes must end with `Service` or `ServiceImpl`; `@RestController` classes must end with `Controller`; `@Repository` classes must end with `Repository`                                                                                                 |
| `konsist-non-blocking`     | `NonBlockingRulesTest`       | Functions must not use `Thread.sleep`, `Thread::sleep`, `CountDownLatch`, `runBlocking`, or `GlobalScope`                                                                                                                                                           |
| `konsist-test-conventions` | `TestConventionRulesTest`    | Unit tests must not load a Spring context (no `@SpringBootTest`, `@WebMvcTest`, `@DataMongoTest`, `@AutoConfigureMockMvc`); test class package must match the class-under-test package                                                                              |

All rule classes ship with `@ExtendWith(BaselineExtension::class)` — the [junit-baseline-extension](https://github.com/beigirad/junit-baseline-extension) records a snapshot of existing violations on the first run and only fails on **new** violations thereafter. It is included transitively via the `api` dependency scope — no extra dependency needed.

## How It Works

### Custom JUnit Platform TestEngine

`KonsistTestEngine` implements `org.junit.platform.engine.TestEngine` and is registered via `META-INF/services/`. At test time it:

1. **Discovers** rule classes in `ir.tapsell.konsist.rules` using [ClassGraph](https://github.com/classgraph/classgraph) (handles Spring Boot fat JARs with custom URL protocols).
2. **Builds a test tree** — each class becomes a CONTAINER, each `@Test` method becomes a TEST. Classes with no `@Test` methods are silently dropped.
3. **Executes** each method, respecting `@ExtendWith` annotations — invokes JUnit Jupiter extension lifecycle callbacks in order:
   - `BeforeAllCallback` → `BeforeEachCallback` → the test method → `TestExecutionExceptionHandler` (if it threw) → `AfterEachCallback` → `AfterAllCallback`
4. **Reports** results through the JUnit Platform launcher — failures appear in your IDE and CI reports like any other test.

### ClassGraph vs hand-rolled classpath scanning

Spring Boot fat JARs use a custom `jar:nested:...` URL protocol that `java.net.JarURLConnection` cannot open. ClassGraph handles this transparently, ensuring rules are always discovered regardless of how the consuming project is packaged.

## Heuristic Text Matching

`GeneralConventionRulesTest` and `NonBlockingRulesTest` use raw substring matching against `function.text` (the function body source) rather than AST analysis. This means matches inside string literals or comments will also trigger. This is an intentional simplicity trade-off — it's "good enough as a guard-rail" and documented in the KDoc of those classes.

## Requirements

| Dependency               | Version      | Scope                                 |
|--------------------------|--------------|---------------------------------------|
| Kotlin                   | 2.0.0        | JVM toolchain 17                      |
| Gradle                   | 9.0.0        | Wrapper included                      |
| Konsist                  | 0.17.3       | `api` (transitive to consumers)       |
| JUnit Jupiter            | 5.10.2 (BOM) | `api` (transitive to consumers)       |
| JUnit Platform Engine    | 5.10.2       | `api` (transitive to consumers)       |
| junit-baseline-extension | 1.7          | `api` (transitive to consumers)       |
| ClassGraph               | 4.8.184      | `implementation` (internal only)      |
| Spring Framework         | 6.2.7 (BOM)  | `compileOnly` (consumer supplies own) |

