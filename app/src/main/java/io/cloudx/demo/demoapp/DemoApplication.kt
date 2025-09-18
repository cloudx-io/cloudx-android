package io.cloudx.demo.demoapp

import android.app.Application
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXInitializationListener
import io.cloudx.sdk.internal.CXLogger

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enforce logging for the demo app regardless of build variant
        CloudX.setLoggingEnabled(true)

        // Enable Meta test mode for demo app
        enableMetaAudienceNetworkTestMode(true)

        // Initialize CloudX SDK automatically on app startup
        initializeCloudXSdk()
    }

    private fun initializeCloudXSdk() {
        // Get the settings for SDK initialization
        val settings = settings()

        CXLogger.i(TAG, "🚀 Auto-initializing CloudX SDK on app startup")
        CXLogger.i(TAG, "AppKey: ${settings.appKey}, Endpoint: ${settings.initUrl}")

        // Use the CloudXInitializer which now accepts Context
        CloudXInitializer.initializeCloudX(
            context = this,
            settings = settings,
            logTag = TAG,
            listener = object : CloudXInitializationListener {
                override fun onInitialized() {
                    CXLogger.i(TAG, "✅ CloudX SDK initialized successfully")
                }

                override fun onInitializationFailed(cloudXError: CloudXError) {
                    CXLogger.i(
                        TAG,
                        "❌ CloudX SDK initialization failed: ${cloudXError.effectiveMessage}"
                    )
                }
            }
        )
    }

    companion object {
        private const val TAG = "DemoApplication"
    }
}