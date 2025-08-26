package io.cloudx.sdk.internal.core.ad.suspendable

import kotlinx.coroutines.flow.StateFlow

interface LastErrorEvent {

    val lastErrorEvent: StateFlow<io.cloudx.sdk.internal.adapter.CloudXAdapterError?>
}