package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.internal.network.httpclient.CloudXHttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object LossReporter {

    fun fireLoss(lurl: String?, reason: LossReason) {
        if (lurl == null) return

        val resolvedLurl = lurl.replace("\${AUCTION_LOSS}", reason.code.toString())
        val finalLurl = resolvedLurl.replace("\${AUCTION_PRICE}", "")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                CloudXHttpClient().get(finalLurl)
            } catch (e: Exception) {
                // Log the error if needed, but do not block the main thread.
                // This is a fire-and-forget operation.
            }
        }
    }
}
