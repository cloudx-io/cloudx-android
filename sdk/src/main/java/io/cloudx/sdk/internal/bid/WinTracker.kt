package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

// TODO: TEMPORARY. Remove this and integrate into MetricsTrackerNew once it's ready.
internal object WinTracker {

    private val TAG = "WinTracker"

    suspend fun trackWin(
        placementName: String,
        placementId: String,
        nurl: String?,
        revenue: Double?
    ) {
        nurl?.let { url ->
            val completeUrl = url.replace("\${AUCTION_PRICE}", revenue?.toString() ?: "")
            CloudXLogger.d(TAG, placementName, placementId, "Tracking win: $completeUrl")

            try {
                val response: HttpResponse = CloudXHttpClient().get(completeUrl)
                if (!response.status.isSuccess()) {
                    CloudXLogger.w(
                        TAG,
                        placementName,
                        placementId,
                        "Win tracking failed with status: ${response.status}"
                    )
                }
            } catch (e: Exception) {
                CloudXLogger.e(
                    TAG,
                    placementName,
                    placementId,
                    "Win tracking failed with error",
                    e
                )
            }
        }
    }
}