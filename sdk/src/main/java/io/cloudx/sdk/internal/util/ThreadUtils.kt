package io.cloudx.sdk.internal.util

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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
internal object ThreadUtils : Destroyable {

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

    // Application-wide coroutine scope for SDK operations
    // Note: This scope defines lifecycle, not threading - operations should explicitly choose dispatcher
    val ApplicationScope = CoroutineScope(SupervisorJob())

    // UI-focused coroutine scope for main thread operations
    // Note: Use this for UI-related work, ad display, listener callbacks
    val UIScope = CoroutineScope(SupervisorJob() + MainDispatcher)

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
            CXLogger.w("ThreadUtils", "Operation timed out after ${timeoutMs}ms")
            null
        } catch (e: CancellationException) {
            CXLogger.d("ThreadUtils", "Operation was cancelled")
            throw e // Re-throw cancellation to maintain coroutine semantics
        } catch (e: Exception) {
            CXLogger.e("ThreadUtils", "Operation failed with exception")
            null
        }
    }

    /**
     * Shutdown all thread pools.
     *
     * This should only be called when the SDK is being completely shut down.
     */
    override fun destroy() {
        try {
            databaseExecutor.shutdown()

            // Cancel all coroutine scopes
            ApplicationScope.cancel()
            UIScope.cancel()
        } catch (e: Exception) {
            CXLogger.w("ThreadUtils", "Error during thread pool shutdown")
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
