package ir.tapsell.konsist.engine

import io.github.classgraph.ClassGraph
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.junit.platform.engine.ConfigurationParameters
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.TestTag
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Optional

/**
 * JUnit Platform [TestEngine] that discovers and executes Konsist architecture rules.
 *
 * Registered via `META-INF/services/org.junit.platform.engine.TestEngine` so JUnit's
 * launcher picks it up automatically — no explicit registration needed in consuming projects.
 *
 * Discovery scans `ir.tapsell.konsist.rules` for classes that contain at least one
 * [@Test][Test]-annotated method (see [RULE_CLASSES]). Each such class becomes a
 * [KonsistClassDescriptor] (CONTAINER) whose methods become [KonsistMethodDescriptor]s (TEST).
 *
 * ## Extension support
 *
 * The engine respects [@ExtendWith][ExtendWith] annotations on rule classes
 * and invokes the declared extensions' lifecycle callbacks in the standard
 * JUnit Jupiter order:
 *
 *  1. [BeforeAllCallback] – once per class
 *  2. [BeforeEachCallback] – before each [@Test][Test] method
 *  3. the [@Test][Test] method itself
 *  4. [TestExecutionExceptionHandler] – if the method threw (BaselineExtension
 *     records the failure here without re-throwing)
 *  5. [AfterEachCallback] – after each method (BaselineExtension writes /
 *     compares the per-method baseline here)
 *  6. [AfterAllCallback] – once per class (BaselineExtension writes /
 *     compares the per-class baseline here)
 *
 * Only the extension interfaces listed above are honoured; other Jupiter
 * extension contracts (parameter resolution, test-template, lifecycle
 * condition, etc.) are silently ignored because the custom engine does
 * not implement their orchestration.
 */
class KonsistTestEngine : TestEngine {

    /** Stable engine ID used by JUnit Platform to route discovery/execution requests. */
    override fun getId(): String = "tapsell-konsist"

    /**
     * Builds the test tree from [RULE_CLASSES].
     *
     * Classes with no [@Test][Test] methods are silently dropped so empty rule files
     * do not pollute the test report.
     */
    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Tapsell Konsist Rules")

        for (ruleClass in RULE_CLASSES) {
            val classDescriptor = buildClassDescriptor(uniqueId, ruleClass)
            if (classDescriptor.children.isNotEmpty()) {
                engineDescriptor.addChild(classDescriptor)
            }
        }

        return engineDescriptor
    }

    /** Wraps [ruleClass] in a [KonsistClassDescriptor] and attaches one child per [@Test][Test] method. */
    private fun buildClassDescriptor(engineId: UniqueId, ruleClass: Class<*>): KonsistClassDescriptor {
        val classId = engineId.append("class", ruleClass.name)
        val classDescriptor = KonsistClassDescriptor(classId, ruleClass)

        ruleClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .sortedBy { it.name }
            .forEach { method ->
                classDescriptor.addChild(KonsistMethodDescriptor(classId.append("method", method.name), method))
            }

        return classDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val engine = request.rootTestDescriptor
        val listener = request.engineExecutionListener
        val configParams = request.configurationParameters

        listener.executionStarted(engine)

        engine.children
            .filterIsInstance<KonsistClassDescriptor>()
            .forEach { executeClass(it, listener, configParams) }

        listener.executionFinished(engine, TestExecutionResult.successful())
    }

    /**
     * Instantiates the rule class, reads [@ExtendWith][ExtendWith] annotations,
     * and runs each method inside the standard lifecycle callback sequence.
     *
     * Construction or [BeforeAllCallback] failure is reported as a class-level
     * failure so the remaining classes still execute.
     */
    private fun executeClass(
        classDescriptor: KonsistClassDescriptor,
        listener: EngineExecutionListener,
        configParams: ConfigurationParameters,
    ) {
        listener.executionStarted(classDescriptor)

        // -- instantiate the test class --
        val instance = runCatchingReporting(classDescriptor, listener) {
            classDescriptor.testClass.getDeclaredConstructor().newInstance()
        } ?: return

        // -- instantiate extensions declared via @ExtendWith on the class --
        val extensions = classDescriptor.testClass
            .getAnnotationsByType(ExtendWith::class.java)
            .flatMap { it.value.toList() }
            .mapNotNull { extClass ->
                runCatchingReporting(
                    classDescriptor,
                    listener,
                    mapError = { e ->
                        RuntimeException("Failed to instantiate extension ${extClass.java.name}: ${e.message}", e)
                    },
                ) {
                    extClass.java.getDeclaredConstructor().newInstance()
                } ?: return
            }
        val registry = ExtensionRegistry(extensions)

        val classContext = KonsistExtensionContext(
            configurationParameters = configParams,
            testClass = classDescriptor.testClass,
            testInstance = instance,
            uniqueId = classDescriptor.uniqueId.toString(),
        )

        // -- beforeAll callbacks --
        if (!runLifecycleStage(classDescriptor, listener) {
                registry.beforeAll.forEach { it.beforeAll(classContext) }
            }
        ) {
            return
        }

        // -- run each method with beforeEach / afterEach / exception-handling --
        classDescriptor.children
            .filterIsInstance<KonsistMethodDescriptor>()
            .forEach { executeMethod(it, instance, listener, registry, classContext, configParams) }

        // -- afterAll callbacks (best-effort; failure overrides the class result) --
        if (!runLifecycleStage(classDescriptor, listener) {
                registry.afterAll.forEach { it.afterAll(classContext) }
            }
        ) {
            return
        }

        listener.executionFinished(classDescriptor, TestExecutionResult.successful())
    }

    /**
     * Invokes a single rule method wrapped with [BeforeEachCallback],
     * [TestExecutionExceptionHandler], and [AfterEachCallback].
     */
    private fun executeMethod(
        methodDescriptor: KonsistMethodDescriptor,
        instance: Any,
        listener: EngineExecutionListener,
        registry: ExtensionRegistry,
        parentContext: KonsistExtensionContext,
        configParams: ConfigurationParameters,
    ) {
        val methodContext = KonsistExtensionContext(
            configurationParameters = configParams,
            testClass = methodDescriptor.method.declaringClass,
            testMethod = methodDescriptor.method,
            testInstance = instance,
            parentContext = parentContext,
            uniqueId = methodDescriptor.uniqueId.toString(),
        )

        listener.executionStarted(methodDescriptor)

        // -- beforeEach callbacks --
        if (!runLifecycleStage(methodDescriptor, listener) {
                registry.beforeEach.forEach { it.beforeEach(methodContext) }
            }
        ) {
            return
        }

        // -- invoke the test method --
        var throwable: Throwable? = null
        try {
            methodDescriptor.method.invoke(instance)
        } catch (e: InvocationTargetException) {
            throwable = e.cause ?: e
        } catch (e: Exception) {
            throwable = e
        }

        // -- exception handling chain --
        var result = if (throwable != null) {
            resolveExceptionResult(registry.exceptionHandlers, methodContext, throwable)
        } else {
            TestExecutionResult.successful()
        }

        // -- afterEach callbacks (best-effort; failure overrides success) --
        try {
            registry.afterEach.forEach { it.afterEach(methodContext) }
        } catch (e: Exception) {
            result = TestExecutionResult.failed(e)
        }

        listener.executionFinished(methodDescriptor, result)
    }

    /**
     * Runs [block] and returns its result, or reports [descriptor] as failed to [listener]
     * and returns `null` if [block] throws. [mapError] lets callers wrap the caught exception
     * with extra context before it is reported.
     */
    private inline fun <T> runCatchingReporting(
        descriptor: TestDescriptor,
        listener: EngineExecutionListener,
        mapError: (Exception) -> Throwable = { it },
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (e: Exception) {
            listener.executionFinished(descriptor, TestExecutionResult.failed(mapError(e)))
            null
        }

    /**
     * Runs [block] and returns `true`, or reports [descriptor] as failed to [listener]
     * and returns `false` if [block] throws.
     */
    private inline fun runLifecycleStage(
        descriptor: TestDescriptor,
        listener: EngineExecutionListener,
        block: () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: Exception) {
            listener.executionFinished(descriptor, TestExecutionResult.failed(e))
            false
        }

    /**
     * Walks [exceptionHandlers] in order, feeding each one [throwable] (or whatever the
     * previous handler threw). The first handler that returns without throwing is
     * considered to have handled the exception.
     */
    private fun resolveExceptionResult(
        exceptionHandlers: List<TestExecutionExceptionHandler>,
        context: KonsistExtensionContext,
        throwable: Throwable,
    ): TestExecutionResult {
        var currentThrowable = throwable
        for (handler in exceptionHandlers) {
            try {
                handler.handleTestExecutionException(context, currentThrowable)
                return TestExecutionResult.successful()
            } catch (e: Throwable) {
                currentThrowable = e
            }
        }
        return TestExecutionResult.failed(currentThrowable)
    }

    /** Extensions declared via [@ExtendWith][ExtendWith], pre-partitioned by callback interface. */
    private class ExtensionRegistry(extensions: List<Any>) {
        val beforeAll = extensions.filterIsInstance<BeforeAllCallback>()
        val beforeEach = extensions.filterIsInstance<BeforeEachCallback>()
        val exceptionHandlers = extensions.filterIsInstance<TestExecutionExceptionHandler>()
        val afterEach = extensions.filterIsInstance<AfterEachCallback>()
        val afterAll = extensions.filterIsInstance<AfterAllCallback>()
    }

    private companion object {
        /**
         * All rule classes in `ir.tapsell.konsist.rules`, discovered lazily at first use.
         *
         * ClassGraph is used instead of a hand-rolled JDK solution because Spring Boot fat
         * JARs use a custom URL protocol (`jar:nested:…`) that [java.net.JarURLConnection]
         * cannot open, causing standard classpath scanning to miss classes entirely.
         */
        val RULE_CLASSES: List<Class<*>> by lazy {
            ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .acceptPackages("ir.tapsell.konsist.rules")
                .scan()
                .use { result ->
                    result.getClassesWithMethodAnnotation(Test::class.java)
                        .loadClasses()
                }
        }
    }
}

/**
 * CONTAINER descriptor for a Konsist rule class.
 *
 * Forwards the class-level [@Tag][Tag] annotations to the JUnit Platform so consumers
 * can filter rule sets with `--include-tag konsist-<area>`.
 */
class KonsistClassDescriptor(
    uniqueId: UniqueId,
    val testClass: Class<*>,
) : AbstractTestDescriptor(uniqueId, testClass.simpleName) {

    private val source: TestSource = ClassSource.from(testClass)

    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
    override fun getSource(): Optional<TestSource> = Optional.of(source)
    override fun getTags(): Set<TestTag> =
        testClass.getAnnotationsByType(Tag::class.java)
            .mapTo(mutableSetOf()) { TestTag.create(it.value) }
}

/** TEST descriptor for a single [@Test][Test]-annotated rule method. */
class KonsistMethodDescriptor(
    uniqueId: UniqueId,
    val method: Method,
) : AbstractTestDescriptor(uniqueId, method.name) {

    private val source: TestSource = MethodSource.from(method.declaringClass, method)

    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    override fun getSource(): Optional<TestSource> = Optional.of(source)
}