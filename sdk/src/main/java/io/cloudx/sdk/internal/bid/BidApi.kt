package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.httpclient.postJsonWithRetry
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.withIOContext
import io.ktor.client.HttpClient
import org.json.JSONObject

/**
 * Sends ortb compliant Bid request to auction server and gets ortb Bid response (or error) back.
 */
internal class BidApi(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient = CloudXHttpClient()
) {

    // TODO: removed `operator` keyword for better readability. Check consequences!
    suspend fun invoke(
        appKey: String,
        bidRequest: JSONObject
    ): Result<BidResponse, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, json ->
                val parseResult = jsonToBidResponse(json)
                if (parseResult is Result.Success) {
                    TrackingFieldResolver.setResponseData(
                        parseResult.value.auctionId,
                        JSONObject(json)
                    )
                }
                parseResult
            },
            onNoContent = { response, _ ->
                val xStatus = response.headers[HEADER_CLOUDX_STATUS]
                if (xStatus == STATUS_ADS_DISABLED) {
                    Result.Failure(CloudXError(CloudXErrorCode.ADS_DISABLED))
                } else {
                    Result.Failure(CloudXError(CloudXErrorCode.NO_FILL))
                }
            }
        ) {
            httpClient.postJsonWithRetry(
                url = endpointUrl,
                appKey = appKey,
                jsonBody = bidRequest.toString(),
                timeoutMillis = timeoutMillis,
                retryMax = 1,
            )
        }
    }
}
