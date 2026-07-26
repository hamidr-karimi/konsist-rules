package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/**
 * Naming conventions for Spring stereotypes.
 *
 * NOTE: requires Spring on the test classpath — `@Service`, `@Repository`,
 * and `@RestController` are matched by *annotation*, so an unannotated class
 * named `FooController` is intentionally NOT checked here.
 */
@Tag("konsist-naming")
@ExtendWith(BaselineExtension::class)
class NamingConventionRulesTest {

    /**
     * Every `@Service` class must be named `*Service` or `*ServiceImpl`.
     *
     * Matched by annotation type, not by name substring — an unannotated
     * class called `FooService` is intentionally ignored.
     */
    @Test
    fun `services must end with Service or ServiceImpl`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(Service::class)
            .sortedBy { it.location }
            .assertTrue {
                it.name.endsWith("ServiceImpl")
                    || it.name.endsWith("Service")
            }
    }

    /**
     * Every `@RestController` class must be named `*Controller`.
     */
    @Test
    fun `controllers must end with Controller`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(RestController::class)
            .sortedBy { it.location }
            .assertTrue { it.name.endsWith("Controller") }
    }

    /**
     * Every `@Repository` class must be named `*Repository`.
     */
    @Test
    fun `repositories must end with Repository`() {
        Konsist.scopeFromProduction()
            .classes()
            .withAnnotationOf(Repository::class)
            .sortedBy { it.location }
            .assertTrue { it.name.endsWith("Repository") }
    }
}