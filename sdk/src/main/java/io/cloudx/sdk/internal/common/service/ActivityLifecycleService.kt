package io.cloudx.sdk.internal.common.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

interface ActivityLifecycleService {

    val currentResumedActivity: StateFlow<Activity?>
    suspend fun awaitActivityResume(activity: Activity)
}

internal fun ActivityLifecycleService(): ActivityLifecycleService = LazySingleInstance

private val LazySingleInstance by lazy {
    ActivityLifecycleServiceImpl(io.cloudx.sdk.internal.Application())
}

private class ActivityLifecycleServiceImpl(app: Application) :
    ActivityLifecycleService, Application.ActivityLifecycleCallbacks {

    private var _currentResumedActivity = MutableStateFlow<Activity?>(null)
    override val currentResumedActivity: StateFlow<Activity?> = _currentResumedActivity

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}

    override fun onActivityStarted(p0: Activity) {}

    override fun onActivityResumed(p0: Activity) {
        _currentResumedActivity.value = p0
    }

    override fun onActivityPaused(p0: Activity) {
        _currentResumedActivity.compareAndSet(p0, null)
    }

    override fun onActivityStopped(p0: Activity) {}

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}

    override fun onActivityDestroyed(p0: Activity) {
        _currentResumedActivity.compareAndSet(p0, null)
    }

    override suspend fun awaitActivityResume(activity: Activity) {
        _currentResumedActivity.first { it == activity }
    }
}