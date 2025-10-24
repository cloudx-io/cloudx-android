package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXDestroyable

interface CXAdapterDelegate : AdTimeoutEvent, LastErrorEvent, CloudXDestroyable, CloudXAd {
    suspend fun load(): Boolean
}
