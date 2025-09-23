package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.CloudXDestroyable
import io.cloudx.sdk.internal.common.SuspendableTimer
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class BannerSuspendableTimer(
    bannerContainerVisibility: StateFlow<Boolean>,
) : CloudXDestroyable {

    private val scope = ThreadUtils.createMainScope("BannerSuspendableTimer")
    private val suspendableTimer = SuspendableTimer()

    // State tracking
    private var isVisible = false
    var isManuallyEnabled = true // Start enabled by default

    init {
        // Initialize visibility state tracking
        scope.launch {
            bannerContainerVisibility.collect { visible ->
                isVisible = visible
                updateTimerState()
            }
        }
    }

    suspend fun awaitTimeout(millis: Long) {
        with(suspendableTimer) {
            reset(millis, autoStart = false)
            updateTimerState()
            awaitTimeout()
        }
    }

    /**
     * Pause the banner refresh timer manually.
     * This stops the timer without canceling the refresh cycle.
     */
    fun pause() {
        isManuallyEnabled = false
        updateTimerState()
    }

    /**
     * Resume the banner refresh timer manually.
     * This restarts the timer where it left off.
     */
    fun resume() {
        isManuallyEnabled = true
        updateTimerState()
    }

    override fun destroy() {
        scope.cancel()
        suspendableTimer.destroy()
    }

    /**
     * Update timer state based on both visibility and manual enable/disable state.
     * Timer only runs when both conditions are true.
     */
    private fun updateTimerState() {
        val shouldRun = isVisible && isManuallyEnabled
        suspendableTimer.resumeOrPause(shouldRun)
    }

    private fun SuspendableTimer.resumeOrPause(resume: Boolean) {
        if (resume) resume() else pause()
    }
}