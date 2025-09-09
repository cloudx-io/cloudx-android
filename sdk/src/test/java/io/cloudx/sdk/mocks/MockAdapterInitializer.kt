package io.cloudx.sdk.mocks

import android.content.Context
import android.os.Bundle
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import kotlinx.coroutines.flow.StateFlow

internal class MockAdapterInitializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult {
        return CloudXAdapterInitializationResult.Success
    }
}
