package io.cloudx.sdk.internal.ads.fullscreen

import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.ads.CXAdapterDelegate
import kotlinx.coroutines.flow.SharedFlow

// TODO. Refactor. This should do for now.
internal interface FullscreenAdAdapterDelegate<Event> : CXAdapterDelegate,
    CloudXAdLoadOperationAvailability {
    fun show()

    val event: SharedFlow<Event>
}