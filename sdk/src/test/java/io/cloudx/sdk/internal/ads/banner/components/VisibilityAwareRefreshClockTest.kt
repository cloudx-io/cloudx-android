package io.cloudx.sdk.internal.ads.banner.components

import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VisibilityAwareRefreshClockTest {

    @Test
    fun `no stacking - next tick only after full interval post-finish`() = runTest(StandardTestDispatcher()) {
        val scope = TestScope(this.testScheduler)
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = scope)

        clock.setVisible(true)
        clock.start()

        var ticks = 0
        val collectJob = scope.backgroundScope.launch {
            clock.ticks.collect { ticks++ }
        }

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
    }

    @Test
    fun `hidden elapsed, became visible during in-flight - emit on finish`() = runTest(StandardTestDispatcher()) {
        val scope = TestScope(this.testScheduler)
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = scope)

        clock.setVisible(false)
        clock.start()

        var ticks = 0
        val collectJob = scope.backgroundScope.launch {
            clock.ticks.collect { ticks++ }
        }

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
    }

    @Test
    fun `hidden not in-flight - emit immediately on visible`() = runTest(StandardTestDispatcher()) {
        val scope = TestScope(this.testScheduler)
        val clock = VisibilityAwareRefreshClock(intervalMs = 1_000, scope = scope)

        clock.setVisible(false)
        clock.start()

        var ticks = 0
        val collectJob = scope.backgroundScope.launch {
            clock.ticks.collect { ticks++ }
}

        advanceTimeBy(1_500)
        runCurrent()

        // Become visible (not in-flight) -> emit immediate tick
        clock.setVisible(true)
        runCurrent()
        assertEquals(1, ticks)

        collectJob.cancel()
    }
}
