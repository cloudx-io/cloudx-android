package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.httpclient.postJsonWithRetry
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class BidApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : BidApi {

    private val tag = "BidApiImpl"

    override suspend fun invoke(
        appKey: String,
        bidRequest: JSONObject
    ): Result<BidResponse, CloudXError> = httpCatching(
        tag = tag,
        onOk = { json -> 
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
        val body = withContext(Dispatchers.IO) {
            bidRequest.toString().also { CXLogger.d(tag, "Serialized body (${it.length} chars)") }
        }
        httpClient.postJsonWithRetry(
            url = endpointUrl,
            appKey = appKey,
            jsonBody = body,
            timeoutMillis = timeoutMillis,
            retryMax = 1,
            tag = tag
        )
    }
}