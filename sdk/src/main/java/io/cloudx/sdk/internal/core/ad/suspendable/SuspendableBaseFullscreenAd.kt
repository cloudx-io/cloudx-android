package io.cloudx.sdk.internal.core.ad.suspendable

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.CloudXAd
import kotlinx.coroutines.flow.SharedFlow

// TODO. Refactor. This should do for now.
internal interface SuspendableBaseFullscreenAd<Event> : CloudXAdLoadOperationAvailability,
    AdTimeoutEvent,
    LastErrorEvent,
    Destroyable,
    CloudXAd {

    suspend fun load(): Boolean
    fun show()

    val event: SharedFlow<Event>
}