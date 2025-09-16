package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import org.json.JSONObject

/**
 * Sends ortb compliant Bid request to auction server and gets ortb Bid response (or error) back.
 */
internal interface BidApi {

    // TODO: removed `operator` keyword for better readability. Check consequences!
    suspend fun invoke(appKey: String, bidRequest: JSONObject): Result<BidResponse, CloudXError>
}

internal fun BidApi(endpointUrl: String, timeoutMillis: Long): BidApi = BidApiImpl(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = CloudXHttpClient()
)
