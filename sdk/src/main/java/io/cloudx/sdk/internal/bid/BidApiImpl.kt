package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.network.BasePostRequest
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class BidApiImpl(
    endpointUrl: String,
    timeoutMillis: Long,
    httpClient: HttpClient
) : BasePostRequest<BidResponse>(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = httpClient,
    tag = "BidApiImpl"
), BidApi {

    override suspend fun invoke(
        appKey: String,
        bidRequest: JSONObject
    ): Result<BidResponse, CLXError> {
        val requestBody = withContext(Dispatchers.IO) { bidRequest.toString() }
        return execute(appKey, requestBody)
    }

    override fun logRequest(appKey: String, body: String) {
        Logger.d("BidApiImpl", "Bid request → $endpointUrl\nBody: $body")
    }

    override suspend fun parse(json: String): Result<BidResponse, CLXError> =
        jsonToBidResponse(json)

    override fun onParsedSuccess(parsed: BidResponse, rawBody: String, response: HttpResponse) {
        TrackingFieldResolver.setResponseData(parsed.auctionId, JSONObject(rawBody))
    }
}
