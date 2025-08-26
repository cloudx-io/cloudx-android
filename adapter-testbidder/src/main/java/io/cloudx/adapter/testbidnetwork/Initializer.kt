package io.cloudx.adapter.testbidnetwork

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.InitializationResult
import kotlinx.coroutines.flow.StateFlow

object Initializer : CloudXAdapterInitializer {
    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): InitializationResult {
        CloudXLogger.info("TestBidNetworkInitializer", "initialized")
        return InitializationResult.Success
    }
}