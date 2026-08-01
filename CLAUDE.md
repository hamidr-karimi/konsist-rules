# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
./gradlew build      # compile + run tests
./gradlew test       # run tests only
```

## Project Overview

A **shared Konsist architecture-rule library** published as a JAR (`com.github.hamidr-karimi:konsist-rules:1.3`). Consuming Kotlin/Spring projects add it as a test dependency and the rules run as part of their JUnit 5 suite. The library itself is a single-module Gradle project (`java-library` plugin), not a Gradle plugin or annotation processor.

## Rule-Writing Conventions

Every rule class lives in `ir.tapsell.konsist.rules` and follows this pattern:

```kotlin
@Tag("konsist-<area>")
class AreaRules {
    @Test
    fun `descriptive name`() {
        Konsist.scopeFromProduction()
            .classes()                        // or .functions(), .properties(), etc.
            .assertFalse { element ->         // or .assertTrue { … }
                // condition that must NOT hold
            }
    }
}
```

- **`@Tag("konsist-<area>")`** on each class — consumers filter with JUnit Platform tags (e.g., `--include-tag konsist-naming`).
- **`Konsist.scopeFromProduction()`** — scans `src/main` of the *consuming* project at runtime, not this library's own sources.
- **`TestConventionRules`** is the exception: it uses `Konsist.scopeFromTest()` to scan the consumer's `src/test`.
- **`assertFalse`** is the default style — rules describe what must *not* happen. Use `assertTrue` only for positive constraints.

## Dependency Scoping

| Scope            | What                                               | Why                                                                                                                                   |
|------------------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `api`            | `konsist:0.17.3`, `junit-jupiter` (via BOM 5.10.2) | These are part of the rule classes' public API — consumers need them transitively                                                     |
| `compileOnly`    | Spring BOM 6.2.7, `spring-context`, `spring-web`   | Needed only to reference `@RestController`, `@Service`, `@Autowired`, etc. at compile time; consumers supply their own Spring version |
| `implementation` | `classgraph:4.8.184`                               | Used internally by `KonsistTestEngine` to discover rule classes at runtime; consumers don't call ClassGraph directly                  |

## Rule Class Discovery

`KonsistTestEngine` auto-discovers rule classes at runtime using ClassGraph — it scans the `ir.tapsell.konsist.rules` package and loads every class that contains at least one `@Test`-annotated method. **No registration is needed when adding a new rule class.** ClassGraph is used (over a hand-rolled JDK solution) because Spring Boot fat JARs use a custom URL protocol that the standard `JarURLConnection` approach cannot handle.

## Heuristic Text Matching

`GeneralConventionRules` and `NonBlockingRules` use raw substring matching against `function.text` (the function body source) rather than AST analysis. This means matches inside string literals or comments will also trigger. This is an intentional simplicity trade-off documented in the KDoc of those classes.

## Adding a New Rule

1. Create `src/main/kotlin/ir/tapsell/konsist/rules/<Area>Rules.kt`.
2. Add `@Tag("konsist-<kebab-area>")` matching the file's focus.
3. Write `@Test` methods following the `assertFalse`/`assertTrue` pattern.
4. If the rule references Spring annotations, they are available via the existing `compileOnly` dependencies — no build changes needed.
5. If the rule needs a new Konsist API surface, add the dependency as `api` so consumers get it transitively.