package io.cloudx.sdk.internal.startup

import android.content.Context
import androidx.startup.Initializer
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService

internal class ActivityLifecycleServiceStartup : Initializer<Unit> {

    override fun create(context: Context) {
        // TODO. Not pretty.
        // Triggering/registering to activity change events before any activity appearing, due to
        // lack of proper android API
        ActivityLifecycleService()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(ApplicationContextStartup::class.java)
    }
}