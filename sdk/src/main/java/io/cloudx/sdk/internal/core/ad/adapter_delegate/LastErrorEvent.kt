package io.cloudx.sdk.internal.core.ad.adapter_delegate

import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import kotlinx.coroutines.flow.StateFlow

interface LastErrorEvent {

    val lastErrorEvent: StateFlow<CloudXAdapterError?>
}