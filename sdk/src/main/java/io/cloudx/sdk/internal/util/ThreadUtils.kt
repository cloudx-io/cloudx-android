package io.cloudx.sdk.internal.util

import android.os.Handler
import android.os.Looper
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
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

    private val mainHandler = Handler(Looper.getMainLooper())

    // Background executor for I/O operations
    private val ioExecutor = Executors.newFixedThreadPool(
        8, // Limited thread count for mobile
        CloudXThreadFactory("CloudX-IO")
    )

    // Background executor for CPU-intensive operations
    private val cpuExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtMost(4),
        CloudXThreadFactory("CloudX-CPU")
    )

    // Single-threaded executor for database operations
    private val databaseExecutor = Executors.newSingleThreadExecutor(
        CloudXThreadFactory("CloudX-DB")
    )

    // Coroutine dispatchers
    val MainDispatcher: CoroutineDispatcher = Dispatchers.Main
    val IODispatcher: CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
    val CPUDispatcher: CoroutineDispatcher = cpuExecutor.asCoroutineDispatcher()
    val DatabaseDispatcher: CoroutineDispatcher = databaseExecutor.asCoroutineDispatcher()

    // Application-wide coroutine scope for SDK operations
    // Note: This scope lives for the entire SDK lifecycle
    val ApplicationScope = CoroutineScope(SupervisorJob() + IODispatcher)

    /**
     * Check if the current thread is the main UI thread.
     *
     * @return true if running on the main thread
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Execute a runnable on the main UI thread.
     *
     * If already on the main thread, executes immediately.
     * Otherwise, posts to the main thread handler.
     *
     * @param runnable the code to execute
     */
    fun runOnMainThread(runnable: Runnable) {
        if (isMainThread()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    /**
     * Execute a runnable on the main UI thread with a delay.
     *
     * @param runnable the code to execute
     * @param delayMs delay in milliseconds
     */
    fun runOnMainThreadDelayed(runnable: Runnable, delayMs: Long) {
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Remove a pending runnable from the main thread queue.
     *
     * @param runnable the runnable to remove
     */
    fun removeFromMainThread(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    /**
     * Execute a runnable on the I/O thread pool.
     *
     * Use for network operations, file I/O, etc.
     *
     * @param runnable the code to execute
     */
    fun runOnIOThread(runnable: Runnable) {
        ioExecutor.execute(runnable)
    }

    /**
     * Execute a runnable on the CPU thread pool.
     *
     * Use for CPU-intensive operations like image processing.
     *
     * @param runnable the code to execute
     */
    fun runOnCPUThread(runnable: Runnable) {
        cpuExecutor.execute(runnable)
    }

    /**
     * Execute a runnable on the database thread.
     *
     * Use for all database operations to ensure serial execution.
     *
     * @param runnable the code to execute
     */
    fun runOnDatabaseThread(runnable: Runnable) {
        databaseExecutor.execute(runnable)
    }

    /**
     * Assert that the current thread is the main UI thread.
     *
     * @throws IllegalStateException if not on the main thread
     */
    fun assertMainThread() {
        check(isMainThread()) { "Must be called from the main thread" }
    }

    /**
     * Assert that the current thread is NOT the main UI thread.
     *
     * @throws IllegalStateException if on the main thread
     */
    fun assertBackgroundThread() {
        check(!isMainThread()) { "Must be called from a background thread" }
    }

    /**
     * Create a coroutine scope that automatically handles exceptions.
     *
     * @param context the coroutine context to use
     * @param exceptionHandler optional exception handler
     * @return coroutine scope
     */
    fun createScope(
        context: CoroutineContext = IODispatcher,
        exceptionHandler: CoroutineExceptionHandler? = null
    ): CoroutineScope {
        val job = SupervisorJob()
        val finalContext = if (exceptionHandler != null) {
            context + job + exceptionHandler
        } else {
            context + job
        }
        return CoroutineScope(finalContext)
    }

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
            ioExecutor.shutdown()
            cpuExecutor.shutdown()
            databaseExecutor.shutdown()

            // Cancel all coroutine scopes
            ApplicationScope.cancel()
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
