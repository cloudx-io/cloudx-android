package io.cloudx.sdk.internal.tracker

import android.os.SystemClock
import io.cloudx.sdk.internal.AdType

internal data class SessionMetrics(
    val depth: Float,
    val bannerDepth: Float,
    val mediumRectangleDepth: Float,
    val fullDepth: Float,
    val nativeDepth: Float,
    val rewardedDepth: Float,
    val durationSeconds: Float
)

/**
 * Tracks session metrics for impression frequency across global, format, and placement scopes.
 * Metrics reset after 30 minutes of inactivity or an explicit reset call.
 */
internal object SessionMetricsTracker {

    internal interface Clock {
        fun elapsedRealtime(): Long
    }

    private object SystemClockClock : Clock {
        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    }

    private enum class SessionAdFormat {
        BANNER,
        MEDIUM_RECTANGLE,
        FULL,
        NATIVE,
        REWARDED
    }

    private var clock: Clock = SystemClockClock

    private var sessionStartElapsedRealtime: Long? = null
    private var globalCount: Int = 0
    private val formatCounts = IntArray(SessionAdFormat.entries.size)

    @Synchronized
    fun recordImpression(adType: AdType) {
        val now = clock.elapsedRealtime()

        val format = adType.toSessionFormat()

        if (sessionStartElapsedRealtime == null) {
            sessionStartElapsedRealtime = now
        }

        globalCount += 1
        formatCounts[format.ordinal] = formatCounts[format.ordinal] + 1
    }

    @Synchronized
    fun getMetrics(): SessionMetrics {
        val now = clock.elapsedRealtime()
        val durationSeconds = sessionStartElapsedRealtime
            ?.let { ((now - it) / 1000f).coerceAtLeast(0f) }
            ?: 0f

        return SessionMetrics(
            depth = globalCount.toFloat(),
            bannerDepth = formatCounts[SessionAdFormat.BANNER.ordinal].toFloat(),
            mediumRectangleDepth = formatCounts[SessionAdFormat.MEDIUM_RECTANGLE.ordinal].toFloat(),
            fullDepth = formatCounts[SessionAdFormat.FULL.ordinal].toFloat(),
            nativeDepth = formatCounts[SessionAdFormat.NATIVE.ordinal].toFloat(),
            rewardedDepth = formatCounts[SessionAdFormat.REWARDED.ordinal].toFloat(),
            durationSeconds = durationSeconds
        )
    }

    @Synchronized
    fun resetAll() {
        resetState()
    }

    @Synchronized
    fun setClockForTesting(testClock: Clock) {
        clock = testClock
    }

    @Synchronized
    fun resetClockForTesting() {
        clock = SystemClockClock
    }

    private fun resetState() {
        globalCount = 0
        formatCounts.fill(0)
        sessionStartElapsedRealtime = null
    }

    private fun AdType.toSessionFormat(): SessionAdFormat = when (this) {
        is AdType.Banner.Standard -> SessionAdFormat.BANNER
        is AdType.Banner.MREC -> SessionAdFormat.MEDIUM_RECTANGLE
        AdType.Interstitial -> SessionAdFormat.FULL
        AdType.Rewarded -> SessionAdFormat.REWARDED
        is AdType.Native -> SessionAdFormat.NATIVE
    }
}
