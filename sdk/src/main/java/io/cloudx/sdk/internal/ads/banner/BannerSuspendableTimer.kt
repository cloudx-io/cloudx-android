package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.common.SuspendableTimer
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// TODO. Refactor. Wrong place.
//  Use if (suspendWhenInvisible) BannerSuspendableTimer() else NonSuspendableTimer()
internal class BannerSuspendableTimer(
    bannerContainerVisibility: StateFlow<Boolean>,
    suspendWhenInvisible: Boolean
) : Destroyable {

    private val scope = ThreadUtils.createMainScope("BannerSuspendableTimer")
    private val suspendableTimer = SuspendableTimer()
    private var canCountTime = false

    init {
        if (suspendWhenInvisible) {
            scope.launch {
                bannerContainerVisibility.collect { canCountTime ->
                    this@BannerSuspendableTimer.canCountTime = canCountTime
                    suspendableTimer.resumeOrPause(canCountTime)
                }
            }
        } else {
            canCountTime = true
            suspendableTimer.resumeOrPause(canCountTime)
        }
    }

    suspend fun awaitTimeout(millis: Long) {
        with(suspendableTimer) {
            reset(millis, autoStart = false)
            resumeOrPause(canCountTime)
            awaitTimeout()
        }
    }

    override fun destroy() {
        scope.cancel()
        suspendableTimer.destroy()
    }

    private fun SuspendableTimer.resumeOrPause(resume: Boolean) {
        if (resume) resume() else pause()
    }
}