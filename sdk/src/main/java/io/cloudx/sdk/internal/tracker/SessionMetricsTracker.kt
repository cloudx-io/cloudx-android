package io.cloudx.sdk.internal.tracker

import android.os.SystemClock
import io.cloudx.sdk.internal.AdType
import java.util.concurrent.TimeUnit

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

    private val sessionTimeoutMillis = TimeUnit.MINUTES.toMillis(30)

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
    private var lastActivityElapsedRealtime: Long? = null
    private var globalCount: Int = 0
    private val formatCounts = IntArray(SessionAdFormat.values().size)
    private val placementCounts = mutableMapOf<String, Int>()

    @Synchronized
    fun recordImpression(placementName: String, adType: AdType) {
        val now = clock.elapsedRealtime()
        maybeResetForInactivity(now)

        val format = adType.toSessionFormat()

        if (sessionStartElapsedRealtime == null) {
            sessionStartElapsedRealtime = now
        }
        lastActivityElapsedRealtime = now

        globalCount += 1
        formatCounts[format.ordinal] = formatCounts[format.ordinal] + 1
        placementCounts[placementName] = placementCounts.getOrElse(placementName) { 0 } + 1
    }

    @Synchronized
    fun getMetrics(): SessionMetrics {
        val now = clock.elapsedRealtime()
        maybeResetForInactivity(now)

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
    fun getPlacementDepth(placementName: String): Int =
        placementCounts[placementName] ?: 0

    @Synchronized
    fun resetPlacement(placementName: String) {
        placementCounts.remove(placementName)
    }

    @Synchronized
    fun resetAll() {
        resetState()
        PlacementLoopIndexTracker.resetAll()
    }

    @Synchronized
    fun setClockForTesting(testClock: Clock) {
        clock = testClock
    }

    @Synchronized
    fun resetClockForTesting() {
        clock = SystemClockClock
    }

    private fun maybeResetForInactivity(now: Long) {
        val lastActivity = lastActivityElapsedRealtime ?: return
        if (now - lastActivity >= sessionTimeoutMillis) {
            resetState()
            PlacementLoopIndexTracker.resetAll()
        }
    }

    private fun resetState() {
        globalCount = 0
        formatCounts.fill(0)
        placementCounts.clear()
        sessionStartElapsedRealtime = null
        lastActivityElapsedRealtime = null
    }

    private fun AdType.toSessionFormat(): SessionAdFormat = when (this) {
        is AdType.Banner.Standard -> SessionAdFormat.BANNER
        is AdType.Banner.MREC -> SessionAdFormat.MEDIUM_RECTANGLE
        AdType.Interstitial -> SessionAdFormat.FULL
        AdType.Rewarded -> SessionAdFormat.REWARDED
        is AdType.Native -> SessionAdFormat.NATIVE
    }
}
