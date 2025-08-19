package io.cloudx.adapter.cloudx

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.AdNetworkInitializer
import io.cloudx.sdk.internal.adapter.InitializationResult
import kotlinx.coroutines.flow.StateFlow

object Initializer : AdNetworkInitializer {
    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): InitializationResult {
        CloudXLogger.info("CloudX-DSP Initializer", "initialized")
        return InitializationResult.Success
    }
}