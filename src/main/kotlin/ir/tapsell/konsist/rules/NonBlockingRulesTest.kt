package ir.tapsell.konsist.rules
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import ir.beigirad.junitbaselineextension.BaselineExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Non-blocking / structured-concurrency guard-rails for production code.
 *
 * Heuristic: this matches against `function.text` (the raw source of the
 * function body), so it is a substring search, not semantic analysis. It will
 * also match the literal inside comments or strings, and it cannot resolve an
 * aliased import. Good enough as a guard-rail; extend [forbiddenCalls] with
 * other blocking/unstructured APIs (e.g. `.get(`) as conventions require.
 *
 * Why these literals:
 *  - `Thread.sleep` / `CountDownLatch` — park the calling thread, defeating the
 *    point of non-blocking code.
 *  - `runBlocking` — bridges suspending code into a blocking call, parking the
 *    thread until completion; fine in `main`/tests, a foot-gun in production.
 *  - `GlobalScope` — launches coroutines tied to no lifecycle, so they leak and
 *    swallow failures instead of propagating cancellation. Inject a scoped
 *    `CoroutineScope` instead.
 *
 * `suspend` functions are not checked separately: they are a subset of all
 * functions, and every blocking literal that matters to them is already in
 * [forbiddenCalls].
 */
@Tag("konsist-non-blocking")
@ExtendWith(BaselineExtension::class)
class NonBlockingRulesTest {

    /**
     * Substrings matched against each function's body source
     * ([KoFunctionDeclaration.text]). This is a substring search, not semantic
     * analysis — literals inside comments or strings will also match.
     */
    private val forbiddenCalls = listOf(
        "Thread.sleep",
        "Thread::sleep",
        "CountDownLatch",
        "runBlocking",
        "GlobalScope",
    )

    /**
     * No production function body may contain a call to a blocking or
     * unstructured-concurrency API (`Thread.sleep`, `runBlocking`,
     * `GlobalScope`, etc.).
     */
    @Test
    fun `functions must not call blocking or unstructured-concurrency APIs`() {
        Konsist.scopeFromProduction()
            .functions()
            .sortedBy { it.location }
            .assertFalse { function ->
                forbiddenCalls.any { call -> function.text.contains(call) }
            }
    }
}
