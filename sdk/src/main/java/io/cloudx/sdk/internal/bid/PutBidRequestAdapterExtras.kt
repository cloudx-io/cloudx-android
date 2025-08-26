package io.cloudx.sdk.internal.bid

import android.content.Context
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import org.json.JSONObject

internal suspend fun JSONObject.putBidRequestAdapterExtras(
    context: Context,
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>
) {
    put("adapter_extras", JSONObject().apply {
        bidRequestExtrasProviders.onEach {
            val map = it.value(context)
            if (map.isNullOrEmpty()) return

            put(it.key.toBidRequestString(), JSONObject().apply {
                map.onEach { (k, v) ->
                    put(k, v)
                }
            })
        }
    })
}

internal fun AdNetwork.toBidRequestString() = when (this) {
    AdNetwork.GoogleAdManager -> "googleAdManager"
    AdNetwork.Meta -> "meta"
    AdNetwork.Mintegral -> "mintegral"
    AdNetwork.TestNetwork -> "testbidder"
    AdNetwork.CloudX -> "cloudx"
    AdNetwork.CloudXSecond -> "cloudxsecond"
    is AdNetwork.Unknown -> this.name
}