package io.cloudx.sdk.internal.ads.fullscreen

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.ads.AdTimeoutEvent
import io.cloudx.sdk.internal.ads.LastErrorEvent
import kotlinx.coroutines.flow.SharedFlow

// TODO. Refactor. This should do for now.
internal interface FullscreenAdAdapterDelegate<Event> : CloudXAdLoadOperationAvailability,
    AdTimeoutEvent,
    LastErrorEvent,
    Destroyable,
    CloudXAd {

    suspend fun load(): Boolean
    fun show()

    val event: SharedFlow<Event>
}