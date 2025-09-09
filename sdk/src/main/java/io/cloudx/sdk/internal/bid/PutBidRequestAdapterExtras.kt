package io.cloudx.sdk.internal.bid

import android.content.Context
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.toBidRequestString
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
