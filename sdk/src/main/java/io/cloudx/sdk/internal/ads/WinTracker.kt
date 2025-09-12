package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

// TODO: TEMPORARY. Remove this and integrate into MetricsTrackerNew once it's ready.
internal object WinTracker {
    
    suspend fun trackWin(nurl: String?, revenue: Double?, adType: String) {
        nurl?.let { url ->
            val completeUrl = url.replace("\${AUCTION_PRICE}", revenue?.toString() ?: "")
            CloudXLogger.d("WinTracker", "$adType tracking win: $completeUrl")
            
            try {
                val response: HttpResponse = CloudXHttpClient().get(completeUrl)
                if (!response.status.isSuccess()) {
                    CloudXLogger.w("WinTracker", "$adType win tracking failed with status: ${response.status}")
                }
            } catch (e: Exception) {
                CloudXLogger.e("WinTracker", "$adType win tracking error: ${e.message}")
            }
        }
    }
}