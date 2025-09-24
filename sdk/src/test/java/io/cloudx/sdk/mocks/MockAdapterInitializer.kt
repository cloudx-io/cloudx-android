package io.cloudx.sdk.mocks

import android.content.Context
import android.os.Bundle
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import kotlinx.coroutines.flow.StateFlow

internal class MockAdapterInitializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): Result<Unit, CloudXError> {
        return Unit.toSuccess()
    }
}
