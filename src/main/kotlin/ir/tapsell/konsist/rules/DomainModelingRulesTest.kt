package ir.tapsell.konsist.rules

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Forbids semantically empty types (`Pair`, `Triple`) in function signatures.
 */
@ExtendWith(BaselineExtension::class)
@Tag("konsist-domain-modeling")
class DomainModelingRulesTest {

    /**
     * AVOID `Pair` and `Triple` as function parameters or return types.
     *
     * `Pair` and `Triple` are semantically empty — `first`/`second`/`third`
     * convey no domain meaning. Replace with a named data class or a
     * sealed type that carries intent.
     *
     */
    @Test
    fun `functions must not use Pair or Triple in parameters or return types`() {
        val forbiddenTypeNames = setOf("Pair", "Triple", "kotlin.Pair", "kotlin.Triple")

        Konsist.scopeFromProduction()
            .functions()
            .sortedBy { it.location }
            .assertFalse { fn ->
                val returnName = fn.returnType?.name
                returnName in forbiddenTypeNames ||
                        fn.parameters.any { it.type.name in forbiddenTypeNames }
            }
    }
}
