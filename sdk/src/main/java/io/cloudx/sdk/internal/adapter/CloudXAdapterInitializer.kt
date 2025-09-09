package io.cloudx.sdk.internal.adapter

import android.content.Context
import android.os.Bundle
import io.cloudx.sdk.CloudXPrivacy
import kotlinx.coroutines.flow.StateFlow

interface CloudXAdapterInitializer {

    suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult
}

sealed class CloudXAdapterInitializationResult {
    data object Success : CloudXAdapterInitializationResult()
    class Error(val error: String = "") : CloudXAdapterInitializationResult()
}