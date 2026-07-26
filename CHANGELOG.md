# Changelog

All notable changes to the Konsist architecture-rule library.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.3] — 2026-07-26

### Documentation

- CHANGELOG.md updated with entries for tags 1.1 and 1.2.
- Version bumped in README.md dependency example and build.gradle.kts.

## [1.2] — 2026-07-26

### Changed

- **Deterministic assertion ordering.** All assertions across `GeneralConventionRulesTest`,
  `LayerDependencyRulesTest`, `NamingConventionRulesTest`, `NonBlockingRulesTest`, and
  `TestConventionRulesTest` now call `.sortedBy { it.location }` before `.assertFalse` / `.assertTrue`,
  so violations are reported in a consistent, sorted-by-location order instead of a non-deterministic scan
  order.

## [1.1] — 2026-07-19

### Changed

- **Jitpack badge and link** in README.md updated to point to the `hamidreza-ka/konsist` repository.

### Fixed

- **`@Autowired` detection** in `LayerDependencyRulesTest`. The redundant-`@Autowired` check now uses
  `hasAnnotationOf(Autowired::class)` instead of a raw annotation-name string comparison, making it
  resilient to annotation imports.

### Removed

- **License section** removed from README.md.

### Documentation

- **Rule-tag table** in README.md updated to reflect the 0.1.5 changes (added `konsist-domain-modeling`,
  removed `konsist-package-structure`, updated descriptions).
- **Dependency table** in README.md updated to show junit-baseline-extension 1.7 with `api` scope.
- **Stale KDoc reference** (`@see core-kotlin.md §3.3`) removed from `LayerDependencyRulesTest`.

## [0.1.5] — 2026-07-19

### Added

- **`DomainModelingRulesTest`** (`konsist-domain-modeling`). Enforces the canonical shape of shared `Id` value types
  (`@JvmInline`, `value class` wrapping `UUID`, private constructor, factory methods `from(String)` / `fromOrThrow(String)`
  / `from(UUID)` / `generate()`, and KDoc). Also forbids `Pair` and `Triple` in function signatures — replace with named
  data classes or sealed types.

- **Domain-layer dependency check** in `LayerDependencyRulesTest`. Asserts that classes in `..domain..` packages do not
  depend on any other layer (repository, client, gateway, logger). Uses Konsist's `assertArchitecture` API and is gated
  behind a package-existence guard so it is a no-op in projects without a domain package.

- **Top-level property immutability** check in `ImmutabilityRulesTest`. Extends the existing object/companion-object
  immutability rule to also cover top-level `val`/`var` declarations.

- **Public mutable-collection-return** check in `ImmutabilityRulesTest`. Public functions must not expose mutable
  collection types (`MutableList`, `MutableMap`, etc.) in their return types.

- **`Thread::sleep` to forbidden calls** in `NonBlockingRulesTest`. The method-reference form is now caught alongside the
  existing `Thread.sleep` substring.

### Changed

- **`@Service` naming rule** now accepts both `*Service` and `*ServiceImpl`. Previously required the bare `Service`
  substring anywhere in the name.

- **`junit-baseline-extension` bumped** from 1.6 to 1.7 and promoted from `compileOnly` to `api`. Consumers now get the
  extension transitively instead of having to declare their own dependency.

- **Mutable-collection property check** now excludes `private` properties. A `private var` with a mutable type that is
  never exposed is not a thread-safety risk for external callers.

- **Controller→repository detection** now also flags dependencies on types annotated with `@Repository`, not just types
  whose name ends in `Repository`.

### Removed

- **`PackageStructureRulesTest`** — removed. The package-location rules (controllers in `controller`/`presentation`,
  repositories and entities in `domain`) were too opinionated for a shared library and have been deleted.

### Documentation

- Expanded KDoc across all rule classes. Every test method now documents its scope, confidence level, and rationale.
  Class-level KDoc updated to remove references to deleted files and non-existent rules.

## [0.1.4] — 2026-07-14

### Added

- **Extension lifecycle support in `KonsistTestEngine`.** The engine now processes `@ExtendWith` annotations on rule
  classes and invokes JUnit Jupiter extension lifecycle callbacks in the standard order: `BeforeAllCallback` →
  `BeforeEachCallback` → the test → `TestExecutionExceptionHandler` → `AfterEachCallback` → `AfterAllCallback`. This
  enables the `junit-baseline-extension` to record, compare, and write baselines within the custom engine.

### Supporting changes

- `KonsistExtensionContext` — a minimal `ExtensionContext` implementation that bridges Jupiter extensions into the
  custom `KonsistTestEngine`. Each context level (engine / class / method) has its own namespaced `InMemoryStore`.
- `TestInstancesImpl` — a minimal `TestInstances` provider for single, non-nested test instances.

## [0.1.3] — 2026-07-14

### Fixed

- Reverted `distributionUrl` in the Gradle wrapper to ensure the library can be built and published via Jitpack.

## [0.1.1] — 2026-07-14

### Added

- `@ExtendWith(BaselineExtension::class)` on all rule classes, wiring
  the [junit-baseline-extension](https://github.com/beigirad/junit-baseline-extension) for snapshot/baseline testing.
  Added `compileOnly` dependency on `com.github.beigirad:junit-baseline-extension:1.6`.

## [0.1.0] — 2026-07-13

### Added

- **Custom JUnit Platform `TestEngine`** (`KonsistTestEngine`) that auto-discovers and executes Konsist rule classes at
  runtime.
    - Registered via `META-INF/services/org.junit.platform.engine.TestEngine` — no explicit registration needed in
      consuming projects.
    - Discovery uses [ClassGraph](https://github.com/classgraph/classgraph) to scan `ir.tapsell.konsist.rules`, handling
      Spring Boot fat JARs with custom `jar:nested:...` URL protocols.
    - Forwards class-level `@Tag` annotations to the JUnit Platform so consumers can filter rule sets with
      `--include-tag`.

- **Seven rule classes** covering six architecture categories:

  | Class                        | Tag                         | Rules                                                                                      |
    |------------------------------|-----------------------------|--------------------------------------------------------------------------------------------|
  | `GeneralConventionRulesTest` | `konsist-general`           | No `println`/`print`; no `!!` operator                                                     |
  | `ImmutabilityRulesTest`      | `konsist-immutability`      | Entities must be `data class`; all class/object properties must be immutable `val`         |
  | `LayerDependencyRulesTest`   | `konsist-layer-dependency`  | Controllers must not depend on repositories; no field injection; no redundant `@Autowired` |
  | `NamingConventionRulesTest`  | `konsist-naming`            | Stereotype naming conventions (`@Service`, `@RestController`, `@Repository`)               |
  | `NonBlockingRulesTest`       | `konsist-non-blocking`      | No `Thread.sleep`, `CountDownLatch`, `runBlocking`, `GlobalScope`                          |
  | `PackageStructureRulesTest`  | `konsist-package-structure` | Layer package placement (controllers, repositories, entities)                              |
  | `TestConventionRulesTest`    | `konsist-test-conventions`  | Unit tests must not load Spring context; test package must match class-under-test package  |

- `java-library` Gradle plugin configuration with proper dependency scoping (`api` for transitives, `compileOnly` for
  Spring, `implementation` for ClassGraph).

- Gradle wrapper with Kotlin JVM 2.0.0, JVM toolchain 17, and Gradle 9.0.0.

- `CLAUDE.md` with build instructions, rule-writing conventions, and dependency scoping documentation.

### Fixed

- `ClassGraph` scan now includes `enableMethodInfo()` so `@Test`-annotated methods are correctly discovered when the
  scan configuration was rebuilt.