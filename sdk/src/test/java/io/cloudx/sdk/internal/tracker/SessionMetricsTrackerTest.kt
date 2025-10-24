package io.cloudx.sdk.internal.tracker

// Test workflow triggers
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
        SessionMetricsTracker.recordImpression("banner_placement", AdType.Banner.Standard)
        SessionMetricsTracker.recordImpression("mrec_placement", AdType.Banner.MREC)
        SessionMetricsTracker.recordImpression("fullscreen", AdType.Interstitial)
        SessionMetricsTracker.recordImpression("rewarded", AdType.Rewarded)

        val metrics = SessionMetricsTracker.getMetrics()

        assertEquals(4f, metrics.depth, 0f)
        assertEquals(1f, metrics.bannerDepth, 0f)
        assertEquals(1f, metrics.mediumRectangleDepth, 0f)
        assertEquals(1f, metrics.fullDepth, 0f)
        assertEquals(0f, metrics.nativeDepth, 0f)
        assertEquals(1f, metrics.rewardedDepth, 0f)
    }

    @Test
    fun `resetPlacement clears placement counter only`() {
        SessionMetricsTracker.recordImpression("placement_a", AdType.Banner.Standard)
        SessionMetricsTracker.recordImpression("placement_a", AdType.Banner.Standard)
        SessionMetricsTracker.recordImpression("placement_b", AdType.Rewarded)

        SessionMetricsTracker.resetPlacement("placement_a")

        assertEquals(0, SessionMetricsTracker.getPlacementDepth("placement_a"))
        assertEquals(1, SessionMetricsTracker.getPlacementDepth("placement_b"))
        assertEquals(3f, SessionMetricsTracker.getMetrics().depth, 0f)
    }

    @Test
    fun `session resets after inactivity timeout`() {
        SessionMetricsTracker.recordImpression("placement", AdType.Banner.Standard)
        val timeoutMillis = TimeUnit.MINUTES.toMillis(31)
        clock.advance(timeoutMillis)

        val metrics = SessionMetricsTracker.getMetrics()

        assertEquals(0f, metrics.depth, 0f)
        assertEquals(0f, metrics.durationSeconds, 0f)
        assertEquals(0, SessionMetricsTracker.getPlacementDepth("placement"))
    }

    @Test
    fun `resetAll clears counters and placement loops`() {
        SessionMetricsTracker.recordImpression("loop", AdType.Banner.Standard)
        PlacementLoopIndexTracker.getAndIncrement("loop")

        SessionMetricsTracker.resetAll()

        val metrics = SessionMetricsTracker.getMetrics()
        assertEquals(0f, metrics.depth, 0f)
        assertEquals(0, SessionMetricsTracker.getPlacementDepth("loop"))
        assertEquals(0, PlacementLoopIndexTracker.getCount("loop"))
    }

    private class FakeClock : Clock {
        private var now: Long = 0L

        override fun elapsedRealtime(): Long = now

        fun advance(deltaMillis: Long) {
            now += deltaMillis
        }
    }
}
