package io.cloudx.demo.demoapp

import android.app.Application
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
import io.cloudx.sdk.internal.CloudXLogger

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Enforce logging for the demo app regardless of build variant
        CloudXLogger.isEnabled = true
        
        // Enable Meta test mode for demo app
        enableMetaAudienceNetworkTestMode(true)
        
        // Initialize CloudX SDK automatically on app startup
        initializeCloudXSdk()
    }
    
    private fun initializeCloudXSdk() {
        // Get the settings for SDK initialization
        val settings = settings()
        
        CloudXLogger.i(TAG, "🚀 Auto-initializing CloudX SDK on app startup")
        CloudXLogger.i(TAG, "AppKey: ${settings.appKey}, Endpoint: ${settings.initUrl}")
        
        // Use the CloudXInitializer which now accepts Context
        CloudXInitializer.initializeCloudX(
            context = this,
            settings = settings,
            hashedUserId = null, // Can be set later if needed
            logTag = TAG
        ) { result ->
            val message = if (result.initialized) {
                "✅ CloudX SDK initialized successfully"
            } else {
                "❌ CloudX SDK initialization failed: ${result.description}"
            }
            
            CloudXLogger.i(TAG, message)
        }
    }
    
    companion object {
        private const val TAG = "DemoApplication"
    }
}