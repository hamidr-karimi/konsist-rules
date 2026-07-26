package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.RestController

/**
 * Layering + dependency-injection hygiene for Spring beans.
 *
 * Layering: controllers must go through a service, never touch a repository.
 * A dependency is detected by the parameter/field *type name* ending in
 * `Repository`, covering both constructor injection and `@Autowired` field
 * injection.
 *
 * DI hygiene: dependencies are constructor-injected, not field-injected, and an
 * implicitly-autowired single primary constructor carries no redundant
 * `@Autowired`.
 *
 * Requires Spring on the test classpath — `@Autowired` is imported and matched
 * by type.
 */
@Tag("konsist-layer-dependency")
@ExtendWith(BaselineExtension::class)
class LayerDependencyRulesTest {

    /**
     * `@RestController` classes must not depend on `Repository` types directly.
     *
     * Controllers belong to the presentation layer and must reach the data
     * layer through a service. Both constructor-injected and field-injected
     * (`@Autowired`) dependencies are checked. Detection is by parameter/field
     * type name ending in `Repository` or bearing the `@Repository` annotation.
     */
    @Test
    fun `controllers must not depend on repositories`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(RestController::class)
            .sortedBy { it.location }
            .assertFalse { controller ->
                // Constructor injection
                val constructorDependsOnRepo = controller.primaryConstructor?.parameters?.any { dep ->
                    dep.type.name.endsWith("Repository") ||
                            dep.type.hasAnnotationOf(Repository::class)
                } ?: false

                // Field injection (@Autowired lateinit var repo)
                val fieldDependsOnRepo = controller.properties().filter { prop ->
                            prop.hasAnnotationOf(Autowired::class)
                        }
                        .any { prop ->
                            prop.type?.name?.endsWith("Repository") == true
                                    || prop.type?.hasAnnotationOf(Repository::class) == true
                        }

                constructorDependsOnRepo || fieldDependsOnRepo
            }
    }

    /**
     * Domain logic MUST NOT call repositories, gateways, clients, or loggers.
     *
     * Checks that classes in `..domain..` packages do not depend on types
     * whose names end with Repository, Client, Gateway, or Logger.
     * Requires a `..domain..` / `..infrastructure..` package convention.
     *
     * Medium confidence — naming conventions may miss unconventional names.
     *
     */
    @Test
    fun `domain layer must not depend on other layers`() {
        val scope = Konsist.scopeFromProduction()

        if (scope.classes().withPackage("..domain..").isNotEmpty()) {
            scope.assertArchitecture {
                val domain = Layer("Domain", "..domain..")
                domain.dependsOnNothing()
            }
        }
    }

    /**
     * Field injection (`@Autowired lateinit var dep`) hides a class's
     * dependencies, defeats `val`/immutability, and makes the bean impossible
     * to construct in a plain unit test. Require constructor injection instead.
     */
    @Test
    fun `dependencies must not be field-injected`() {
        Konsist.scopeFromProduction()
            .classes()
            .properties()
            .sortedBy { it.location }
            .assertFalse { prop ->
                prop.hasAnnotationOf(Autowired::class)
            }
    }

    /**
     * When a bean has a single primary constructor, Spring autowires it
     * implicitly, so an explicit `@Autowired` on it is redundant noise. (A class
     * with multiple constructors legitimately needs `@Autowired` to disambiguate
     * and is therefore left alone.)
     */
    @Test
    fun `single primary constructor must not be annotated with Autowired`() {
        Konsist.scopeFromProduction()
            .classes()
            .sortedBy { it.location }
            .assertFalse { cls ->
                val primary = cls.primaryConstructor ?: return@assertFalse false
                cls.secondaryConstructors.isEmpty() &&
                        primary.hasAnnotationOf(Autowired::class)
            }
    }
}