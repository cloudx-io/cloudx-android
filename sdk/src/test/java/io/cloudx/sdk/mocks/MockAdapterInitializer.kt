package io.cloudx.sdk.mocks

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.InitializationResult
import kotlinx.coroutines.flow.StateFlow

internal class MockAdapterInitializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): InitializationResult {
        return InitializationResult.Success
    }
}
