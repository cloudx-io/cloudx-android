package io.cloudx.adapter.cloudx

import android.content.Context
import android.os.Bundle
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import kotlinx.coroutines.flow.StateFlow

object Initializer : CloudXAdapterInitializer {
    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult {
        CXLogger.i("CloudX-DSP Initializer", "initialized")
        return CloudXAdapterInitializationResult.Success
    }
}