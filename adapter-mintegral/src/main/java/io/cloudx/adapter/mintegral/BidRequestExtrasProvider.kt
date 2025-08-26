package io.cloudx.adapter.mintegral

import android.content.Context
import com.mbridge.msdk.mbbid.out.BidManager
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BidRequestExtrasProvider : CloudXAdapterBidRequestExtrasProvider {
    override suspend fun invoke(context: Context): Map<String, String>? {
        return withContext(Dispatchers.Main) {
            BidManager.getBuyerUid(context)?.let {
                mapOf("buyer_id" to it)
            }
        }
    }
}