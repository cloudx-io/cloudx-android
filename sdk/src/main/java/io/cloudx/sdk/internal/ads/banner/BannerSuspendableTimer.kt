package io.cloudx.sdk.internal.ads.banner

import android.app.Activity
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.common.SuspendableTimer
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// TODO. Refactor. Wrong place.
//  Use if (suspendWhenInvisible) BannerSuspendableTimer() else NonSuspendableTimer()
internal class BannerSuspendableTimer(
    private val activity: Activity,
    bannerContainerVisibility: StateFlow<Boolean>,
    activityLifecycleService: ActivityLifecycleService,
    suspendWhenInvisible: Boolean
) : Destroyable {

    // TODO. Consider reusing scopes from outside via lambda creator or something
    //  in order to prevent multiple coroutine scopes. I'm not sure if it's even important for now.
    private val scope = CoroutineScope(Dispatchers.Main)
    private val suspendableTimer = SuspendableTimer()
    private var canCountTime = false

    init {
        if (suspendWhenInvisible) {
            scope.launch {
                activityLifecycleService.currentResumedActivity
                    .combine(bannerContainerVisibility) { currentActivity, bannerVisibility ->
                        currentActivity == this@BannerSuspendableTimer.activity && bannerVisibility
                    }.collect { canCountTime ->
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