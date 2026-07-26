package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Misc. hygiene rules for production code.
 *
 * Heuristic: like [NonBlockingRulesTest], every rule here matches
 * against `function.text` (the raw source of the function body), so it is a
 * substring search, not semantic analysis. It will also match the literal
 * inside comments or strings. Good enough as a guard-rail.
 */
@Tag("konsist-general")
@ExtendWith(BaselineExtension::class)
class GeneralConventionRulesTest {

    /**
     * `println` / `print(` write straight to stdout, bypassing the logging
     * framework (no levels, no structured fields, no appenders). Use a logger.
     */
    @Test
    fun `functions must not call println or print`() {
        Konsist.scopeFromProduction()
            .functions()
            .sortedBy { it.location }
            .assertFalse { function ->
                function.text.contains("println(") || function.text.contains("print(")
            }
    }

    /**
     * The `!!` not-null assertion turns a nullable into a `NullPointerException`
     * at runtime — exactly the failure Kotlin's type system exists to prevent.
     * Prefer `?.`, `?:`, `requireNotNull`, or restructuring the nullability.
     *
     * Caveat: this is a raw substring match, so a `!!` appearing inside a string
     * literal or comment will also be flagged.
     */
    @Test
    fun `functions must not use the not-null assertion operator`() {
        Konsist.scopeFromProduction()
            .functions()
            .sortedBy { it.location }
            .assertFalse { function ->
                function.text.contains("!!")
            }
    }
}