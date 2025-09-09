package io.cloudx.adapter.meta

import android.content.Context
import androidx.annotation.Keep
import com.facebook.ads.BidderTokenProvider
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Keep
internal object BidRequestExtrasProvider : CloudXAdapterBidRequestExtrasProvider {
    override suspend fun invoke(context: Context): Map<String, String>? {
        return withContext(Dispatchers.Main) {
            // Must be called on a background thread
            BidderTokenProvider.getBidderToken(context)?.let {
                mapOf("bidder_token" to it)
            }
        }
    }
}
