package io.cloudx.sdk.internal

import android.util.Log
import io.cloudx.sdk.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Modern, performant logger for CloudX SDK
 */
object CloudXLogger {

    private const val TAG_PREFIX = "CX"
    internal const val DEFAULT_COMPONENT = "SDK"
    private const val LOG_BUFFER_SIZE = 1000

    @Volatile
    var isEnabled: Boolean = BuildConfig.DEBUG

    @Volatile
    var minLogLevel: LogLevel = LogLevel.VERBOSE

    // Non-blocking channel for log events
    private val logChannel = Channel<LogEntry>(capacity = LOG_BUFFER_SIZE)
    val logFlow: Flow<LogEntry> = logChannel.receiveAsFlow()

    // Main logging methods - component first, single letter names
    fun v(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, component, message, throwable)
    }

    fun d(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, component, message, throwable)
    }

    fun i(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, component, message, throwable)
    }

    fun w(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, component, message, throwable)
    }

    fun e(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, component, message, throwable)
    }

    private fun log(
        level: LogLevel,
        component: String,
        message: String,
        throwable: Throwable? = null
    ) {
        if (!isEnabled || level < minLogLevel) return

        val fullTag = createTag(component)

        // Log to Android immediately (most important)
        logToAndroid(level, fullTag, message, throwable)

        // Emit to flow non-blockingly (secondary)
        val logEntry = LogEntry(
            level = level,
            tag = fullTag,
            message = message,
            throwable = throwable,
            timestamp = System.currentTimeMillis()
        )

        // Non-blocking emit - if buffer is full, just drop (logging shouldn't block app)
        logChannel.trySend(logEntry)
    }

    private fun createTag(component: String): String = "$TAG_PREFIX:$component"

    // Placement logging helpers - consistent formatting across codebase
    private fun formatPlacementMessage(
        placementName: String,
        placementId: String,
        message: String
    ): String =
        "[$placementName:$placementId] $message"

    // Placement-specific logging methods
    fun v(
        component: String = DEFAULT_COMPONENT,
        placementName: String,
        placementId: String,
        message: String,
        throwable: Throwable? = null
    ) {
        v(component, formatPlacementMessage(placementName, placementId, message), throwable)
    }

    fun d(
        component: String = DEFAULT_COMPONENT,
        placementName: String,
        placementId: String,
        message: String,
        throwable: Throwable? = null
    ) {
        d(component, formatPlacementMessage(placementName, placementId, message), throwable)
    }

    fun i(
        component: String = DEFAULT_COMPONENT,
        placementName: String,
        placementId: String,
        message: String,
        throwable: Throwable? = null
    ) {
        i(component, formatPlacementMessage(placementName, placementId, message), throwable)
    }

    fun w(
        component: String = DEFAULT_COMPONENT,
        placementName: String,
        placementId: String,
        message: String,
        throwable: Throwable? = null
    ) {
        w(component, formatPlacementMessage(placementName, placementId, message), throwable)
    }

    fun e(
        component: String = DEFAULT_COMPONENT,
        placementName: String,
        placementId: String,
        message: String,
        throwable: Throwable? = null
    ) {
        e(component, formatPlacementMessage(placementName, placementId, message), throwable)
    }

    private fun logToAndroid(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val finalMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }

        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, finalMessage)
            LogLevel.DEBUG -> Log.d(tag, finalMessage)
            LogLevel.INFO -> Log.i(tag, finalMessage)
            LogLevel.WARN -> Log.w(tag, finalMessage)
            LogLevel.ERROR -> Log.e(tag, finalMessage)
        }
    }

    enum class LogLevel(val priority: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4)
    }

    data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long
    )
}