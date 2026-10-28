plugins {
    kotlin("jvm") version "2.0.0"
    `java-library`
    `maven-publish`
}

group = "ir.tapsell"
version = "1.5"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {

    /**
     * Spring annotations are only needed to compile LayerDependencyRules;
     * consuming projects supply their own Spring at runtime
     */
    compileOnly(platform("org.springframework:spring-framework-bom:6.2.7"))
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")

    /** ClassGraph scans the classpath for rule classes at runtime; kept implementation-scoped
     *  because consumers never call ClassGraph directly */
    implementation("io.github.classgraph:classgraph:4.8.184")

    /** Baseline/snapshot testing extension for JUnit 5 */
    api("com.github.beigirad:junit-baseline-extension:1.7")

    /** api = transitive to consumers; both are part of the published rule classes' contract */
    api("com.lemonappdev:konsist:0.17.3")
    api(platform("org.junit:junit-bom:5.10.2"))
    api("org.junit.jupiter:junit-jupiter")

    /**
     * junit-jupiter-engine is runtime-only in the junit-jupiter aggregate; declare explicitly
     * so KonsistTestEngine (which implements TestEngine) can compile against the platform APIs
     */
    api("org.junit.platform:junit-platform-engine")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform {
        // Prevent the engine from running against the library's own sources.
        // Consumers' test tasks include it by default via ServiceLoader.
        excludeEngines("tapsell-konsist")
    }
}

/** Publishing — see "Publishing (manual release)" in README.md. Naming the repository
 *  `TapsellArtifactory` generates the `publishAllPublicationsToTapsellArtifactoryRepository` task. */

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "TapsellArtifactory"
            url = uri("https://reg.production.tpsl.ir/artifactory/maven-tapsell-local")
            credentials {
                username = findProperty("REGISTRY_USERNAME") as String?
                password = findProperty("REGISTRY_PASSWORD") as String?
            }
        }
    }
}
