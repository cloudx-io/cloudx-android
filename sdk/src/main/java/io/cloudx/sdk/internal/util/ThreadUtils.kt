package io.cloudx.sdk.internal.util

import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Thread management utilities for the CloudX SDK.
 *
 * Provides optimized thread pools and executors for different types of operations,
 * ensuring efficient resource usage and proper thread lifecycle management.
 */
object ThreadUtils {

    private const val TAG = "ThreadUtils"
    private val logger = CXLogger.forComponent(TAG)

    // Single-threaded executor for database operations
    private val databaseExecutor = Executors.newSingleThreadExecutor(
        CloudXThreadFactory("CloudX-DB")
    )

    // Coroutine dispatchers - simplified to use standard Kotlin dispatchers
    val MainDispatcher: CoroutineDispatcher = Dispatchers.Main
    val IODispatcher: CoroutineDispatcher = Dispatchers.IO
    val CPUDispatcher: CoroutineDispatcher = Dispatchers.Default

    // Keep custom single-threaded dispatcher for database to ensure serial execution
    val DatabaseDispatcher: CoroutineDispatcher = databaseExecutor.asCoroutineDispatcher()

    // UI-focused coroutine scope for main thread operations
    // Note: Use this for UI-related work, ad display, listener callbacks
    val GlobalMainScope = createMainScope(TAG)

    // Application-wide coroutine scope for SDK operations
    // Note: This scope defines lifecycle, not threading - operations should explicitly choose dispatcher
    val GlobalIOScope = createIOScope(TAG)

    /**
     * Create a component-scoped coroutine scope with proper error handling and lifecycle management.
     *
     * Use this for components that need to cancel operations when destroyed (e.g., BannerManager,
     * AdapterDelegate, timers). Each component gets its own isolated scope with SupervisorJob
     * to prevent one failure from affecting other operations within the component.
     *
     * @param tag Component name for logging (e.g., "BannerManager", "AdapterDelegate")
     * @param dispatcher Coroutine dispatcher to use (defaults to Main for UI components)
     * @return CoroutineScope with SupervisorJob and exception handling
     */
    fun createScope(
        tag: String,
        dispatcher: CoroutineDispatcher = MainDispatcher
    ): CoroutineScope {
        val componentExceptionHandler = CoroutineExceptionHandler { _, exception ->
            CXLogger.e(tag, "Uncaught coroutine exception", exception)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Uncaught coroutine exception in $tag: ${exception.message}",
                errorDetails = exception.stackTraceToString()
            )
        }
        return CoroutineScope(SupervisorJob() + dispatcher + componentExceptionHandler)
    }

    /**
     * Convenience method to create a main-thread component scope (uses Main dispatcher).
     * Use for UI-related components like ad managers, UI controllers, etc.
     */
    fun createMainScope(tag: String): CoroutineScope =
        createScope(tag, MainDispatcher)

    /**
     * Convenience method to create a background component scope (uses IO dispatcher).
     * Use for background components like trackers, network managers, etc.
     */
    fun createIOScope(tag: String): CoroutineScope =
        createScope(tag, IODispatcher)

    /**
     * Execute a suspend function with timeout and error handling.
     *
     * @param timeoutMs timeout in milliseconds
     * @param context coroutine context to use
     * @param block the suspend function to execute
     * @return result or null if timeout/error occurred
     */
    suspend fun <T> executeWithTimeout(
        timeoutMs: Long,
        context: CoroutineContext = IODispatcher,
        block: suspend CoroutineScope.() -> T
    ): T? {
        return try {
            withTimeout(timeoutMs) {
                withContext(context, block)
            }
        } catch (e: TimeoutCancellationException) {
            logger.w("Operation timed out after ${timeoutMs}ms", e)
            null
        } catch (e: CancellationException) {
            logger.d("Operation was cancelled")
            throw e // Re-throw cancellation to maintain coroutine semantics
        } catch (e: Exception) {
            logger.e("Operation failed with exception", e)
            null
        }
    }

    /**
     * Custom thread factory for CloudX SDK threads.
     */
    private class CloudXThreadFactory(private val namePrefix: String) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
            thread.isDaemon = false
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }
}

/**
 * Coroutine extension functions for common patterns.
 */

/**
 * Launch a coroutine on the main thread.
 */
internal fun CoroutineScope.launchMain(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(ThreadUtils.MainDispatcher, block = block)
}

/**
 * Launch a coroutine on the I/O thread.
 */
internal fun CoroutineScope.launchIO(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(ThreadUtils.IODispatcher, block = block)
}

/**
 * Launch a coroutine on the CPU thread.
 */
internal fun CoroutineScope.launchCPU(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(ThreadUtils.CPUDispatcher, block = block)
}

/**
 * Launch a coroutine on the database thread.
 */
internal fun CoroutineScope.launchDatabase(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(ThreadUtils.DatabaseDispatcher, block = block)
}


/**
 * Execute code on the main thread with proper coroutine integration.
 *
 * If already on main thread, executes immediately to avoid unnecessary dispatch.
 */
suspend fun <T> ensureMainContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Main.immediate, block)
}

/**
 * Switch to main thread context.
 */
suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(ThreadUtils.MainDispatcher, block)
}

/**
 * Switch to I/O thread context.
 */
suspend fun <T> withIOContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(ThreadUtils.IODispatcher, block)
}

/**
 * Switch to CPU thread context.
 */
suspend fun <T> withCPUContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(ThreadUtils.CPUDispatcher, block)
}

/**
 * Switch to database thread context.
 */
suspend fun <T> withDatabaseContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(ThreadUtils.DatabaseDispatcher, block)
}
