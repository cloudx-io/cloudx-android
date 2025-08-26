package io.cloudx.sdk.internal.adapter

import android.content.Context
import io.cloudx.sdk.CloudXPrivacy
import kotlinx.coroutines.flow.StateFlow

interface AdapterInitializer {

    suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): InitializationResult
}

sealed class InitializationResult {
    data object Success : InitializationResult()
    class Error(val error: String = "") : InitializationResult()
}