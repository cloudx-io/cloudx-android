package io.cloudx.sdk.internal

import android.util.Log
import io.cloudx.sdk.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant

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
    
    // Main logging methods - component first
    fun verbose(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, component, message, throwable)
    }
    
    fun debug(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, component, message, throwable)
    }
    
    fun info(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, component, message, throwable)
    }
    
    fun warn(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, component, message, throwable)
    }
    
    fun error(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, component, message, throwable)
    }

    // Shorthand methods for compatibility
    fun v(component: String = DEFAULT_COMPONENT, message: String) = verbose(component, message)
    fun d(component: String = DEFAULT_COMPONENT, message: String) = debug(component, message)
    fun i(component: String = DEFAULT_COMPONENT, message: String) = info(component, message)
    fun w(component: String = DEFAULT_COMPONENT, message: String) = warn(component, message)
    fun e(component: String = DEFAULT_COMPONENT, message: String, throwable: Throwable? = null) = error(component, message, throwable)
    
    private fun log(level: LogLevel, component: String, message: String, throwable: Throwable? = null) {
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
            timestamp = Instant.now()
        )
        
        // Non-blocking emit - if buffer is full, just drop (logging shouldn't block app)
        logChannel.trySend(logEntry)
    }
    
    private fun createTag(component: String): String = "$TAG_PREFIX:$component"
    
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
        val timestamp: Instant
    )
}