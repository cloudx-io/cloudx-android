package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.tracker.SessionMetricsTracker.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionMetricsTrackerTest {

    private val clock = FakeClock()

    @Before
    fun setUp() {
        SessionMetricsTracker.setClockForTesting(clock)
        SessionMetricsTracker.resetAll()
    }

    @After
    fun tearDown() {
        SessionMetricsTracker.resetAll()
        SessionMetricsTracker.resetClockForTesting()
    }

    @Test
    fun `recordImpression tracks global and format counts`() {
        SessionMetricsTracker.recordImpression(AdType.Banner.Standard)
        SessionMetricsTracker.recordImpression(AdType.Banner.MREC)
        SessionMetricsTracker.recordImpression(AdType.Interstitial)
        SessionMetricsTracker.recordImpression(AdType.Rewarded)

        val metrics = SessionMetricsTracker.getMetrics()

        assertEquals(4f, metrics.depth, 0f)
        assertEquals(1f, metrics.bannerDepth, 0f)
        assertEquals(1f, metrics.mediumRectangleDepth, 0f)
        assertEquals(1f, metrics.fullDepth, 0f)
        assertEquals(0f, metrics.nativeDepth, 0f)
        assertEquals(1f, metrics.rewardedDepth, 0f)
    }

    @Test
    fun `session duration starts on first impression`() {
        SessionMetricsTracker.recordImpression(AdType.Banner.Standard)
        clock.advance(TimeUnit.SECONDS.toMillis(10))

        val metrics = SessionMetricsTracker.getMetrics()

        assertEquals(1f, metrics.depth, 0f)
        assertEquals(10f, metrics.durationSeconds, 0.1f)
    }

    @Test
    fun `resetAll clears all counters and duration`() {
        SessionMetricsTracker.recordImpression(AdType.Banner.Standard)
        SessionMetricsTracker.recordImpression(AdType.Interstitial)
        clock.advance(TimeUnit.SECONDS.toMillis(60))

        SessionMetricsTracker.resetAll()

        val metrics = SessionMetricsTracker.getMetrics()
        assertEquals(0f, metrics.depth, 0f)
        assertEquals(0f, metrics.bannerDepth, 0f)
        assertEquals(0f, metrics.fullDepth, 0f)
        assertEquals(0f, metrics.durationSeconds, 0f)
    }

    private class FakeClock : Clock {
        private var now: Long = 0L

        override fun elapsedRealtime(): Long = now

        fun advance(deltaMillis: Long) {
            now += deltaMillis
        }
    }
}
