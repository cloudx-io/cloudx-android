package io.cloudx.sdk.internal.adapter

import android.content.Context
import android.os.Bundle
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.flow.StateFlow

interface CloudXAdapterInitializer {

    suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): Result<Unit, CloudXError>
}
