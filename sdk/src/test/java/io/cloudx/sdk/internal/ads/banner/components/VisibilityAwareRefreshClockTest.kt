package io.cloudx.sdk.internal.ads.banner.components

import VisibilityAwareRefreshClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisibilityAwareRefreshClockTest {

    @Test
    fun `no stacking - next tick only after full interval post-finish`() = runTest {
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = this)

        var ticks = 0
        val collectJob = launch { clock.ticks.collect { ticks++ } }
        // Ensure collector is active before we emit the immediate tick
        runCurrent()
        clock.setVisible(true)
        clock.start()

        // Immediate first tick because visible at start
        runCurrent()
        assertEquals(1, ticks)

        // Simulate in-flight request longer than interval
        clock.markRequestStarted()
        advanceTimeBy(5_000)
        runCurrent()

        // Finish request → should NOT emit immediately (no stacking)
        clock.markRequestFinished()
        runCurrent()
        assertEquals(1, ticks)

        // After a full interval, the next tick arrives
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, ticks)

        collectJob.cancel()
        clock.stop()
    }

    @Test
    fun `hidden elapsed, became visible during in-flight - emit on finish`() = runTest {
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = this)

        var ticks = 0
        val collectJob = launch { clock.ticks.collect { ticks++ } }
        clock.setVisible(false)
        clock.start()

        // Hidden long enough to queue hidden tick
        advanceTimeBy(2_000)
        runCurrent()

        // Request starts while still hidden
        clock.markRequestStarted()

        // Become visible during in-flight -> do not emit yet
        clock.setVisible(true)
        runCurrent()
        assertEquals(0, ticks)

        // Finish request -> should emit immediately now
        clock.markRequestFinished()
        runCurrent()
        assertEquals(1, ticks)

        collectJob.cancel()
        clock.stop()
    }

    @Test
    fun `hidden not in-flight - emit immediately on visible`() = runTest {
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = this)

        var ticks = 0
        val collectJob = launch { clock.ticks.collect { ticks++ } }
        clock.setVisible(false)
        clock.start()

        advanceTimeBy(1_500)
        runCurrent()

        // Become visible (not in-flight) -> emit immediate tick
        clock.setVisible(true)
        runCurrent()
        assertEquals(1, ticks)

        collectJob.cancel()
        clock.stop()
    }
}
