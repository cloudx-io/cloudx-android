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
        runCurrent()

        // Become visible: immediate first tick
        clock.setVisible(true)
        clock.start()
        runCurrent()
        assertEquals(1, ticks)

        // Simulate in-flight longer than interval
        clock.markRequestStarted()
        advanceTimeBy(5_000)
        runCurrent()

        // Finish → do NOT emit immediately
        clock.markRequestFinished()
        runCurrent()
        assertEquals(1, ticks)

        // After a full interval, the next tick arrives
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, ticks)

        collectJob.cancel(); clock.stop()
    }

    @Test
    fun `hidden not in-flight - emit immediately on visible`() = runTest {
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = this)

        var ticks = 0
        val collectJob = launch { clock.ticks.collect { ticks++ } }
        runCurrent()

        // Hidden initially
        clock.setVisible(false)
        clock.start()
        runCurrent()

        // Become visible (not in-flight) -> immediate tick
        clock.setVisible(true)
        runCurrent()
        assertEquals(1, ticks)

        collectJob.cancel(); clock.stop()
    }
}

