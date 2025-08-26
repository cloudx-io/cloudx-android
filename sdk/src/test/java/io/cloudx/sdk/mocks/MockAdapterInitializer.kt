package io.cloudx.sdk.mocks

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import kotlinx.coroutines.flow.StateFlow

internal class MockAdapterInitializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult {
        return CloudXAdapterInitializationResult.Success
    }
}
