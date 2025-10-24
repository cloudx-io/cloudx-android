package io.cloudx.adapter.cloudx

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import kotlinx.coroutines.flow.StateFlow

@Keep
object Initializer : CloudXAdapterInitializer {
    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): Result<Unit, CloudXError> {
        CXLogger.i("CloudX-DSP Initializer", "initialized")
        return Unit.toSuccess()
    }
}