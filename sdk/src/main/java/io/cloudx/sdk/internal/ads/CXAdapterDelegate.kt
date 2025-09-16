package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.Destroyable

interface CXAdapterDelegate : AdTimeoutEvent, LastErrorEvent, Destroyable, CloudXAd {
    suspend fun load(): Boolean
}
