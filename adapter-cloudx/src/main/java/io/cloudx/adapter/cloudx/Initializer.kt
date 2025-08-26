package io.cloudx.adapter.cloudx

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import kotlinx.coroutines.flow.StateFlow

object Initializer : CloudXAdapterInitializer {
    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult {
        CloudXLogger.info("CloudX-DSP Initializer", "initialized")
        return CloudXAdapterInitializationResult.Success
    }
}