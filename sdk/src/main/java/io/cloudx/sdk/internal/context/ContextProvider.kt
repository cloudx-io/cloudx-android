package io.cloudx.sdk.internal.context

import android.app.Activity
import android.content.Context
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService

interface ContextProvider {
    fun getContext(): Context
    fun getActivityOrNull(): Activity?
}

fun ContextProvider(): ContextProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    ContextProviderImpl(
        applicationContext = ApplicationContext(),
        activityLifecycleService = ActivityLifecycleService()
    )
}

private class ContextProviderImpl(
    private val applicationContext: Context,
    private val activityLifecycleService: ActivityLifecycleService
) : ContextProvider {
    
    override fun getContext(): Context {
        return getActivityOrNull() ?: applicationContext
    }
    
    override fun getActivityOrNull(): Activity? {
        return activityLifecycleService.currentResumedActivity.value
    }
}