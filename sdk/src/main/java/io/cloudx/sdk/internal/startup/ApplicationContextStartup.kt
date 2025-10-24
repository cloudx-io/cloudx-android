package io.cloudx.sdk.internal.startup

import android.content.Context
import androidx.startup.Initializer
import io.cloudx.sdk.internal.ApplicationContext

// TODO. Addition fallbacks when Jetpack startup fails.
internal class ApplicationContextStartup : Initializer<Unit> {

    override fun create(context: Context) {
        ApplicationContext(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf()
    }
}