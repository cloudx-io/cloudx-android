package io.cloudx.sdk.internal

import android.util.Log
import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.CloudXLogLevel
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
    internal var isEnabled: Boolean = BuildConfig.DEBUG

    @Volatile
    internal var minLogLevel: CloudXLogLevel = CloudXLogLevel.VERBOSE

    // Non-blocking channel for log events
    private val logChannel = Channel<LogEntry>(capacity = LOG_BUFFER_SIZE)
    val logFlow: Flow<LogEntry> = logChannel.receiveAsFlow()

    // Main logging methods - component first, single letter names
    fun v(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(CloudXLogLevel.VERBOSE, component, message, throwable)
    }

    fun d(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(CloudXLogLevel.DEBUG, component, message, throwable)
    }

    fun i(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(CloudXLogLevel.INFO, component, message, throwable)
    }

    fun w(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(CloudXLogLevel.WARN, component, message, throwable)
    }

    fun e(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(CloudXLogLevel.ERROR, component, message, throwable)
    }

    private fun log(
        level: CloudXLogLevel,
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

    private fun logToAndroid(level: CloudXLogLevel, tag: String, message: String, throwable: Throwable?) {
        val finalMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }

        when (level) {
            CloudXLogLevel.VERBOSE -> Log.v(tag, finalMessage)
            CloudXLogLevel.DEBUG -> Log.d(tag, finalMessage)
            CloudXLogLevel.INFO -> Log.i(tag, finalMessage)
            CloudXLogLevel.WARN -> Log.w(tag, finalMessage)
            CloudXLogLevel.ERROR -> Log.e(tag, finalMessage)
        }
    }

    data class LogEntry(
        val level: CloudXLogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val timestamp: Long
    )
}