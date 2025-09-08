package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.network.httpCatching
import io.cloudx.sdk.internal.network.postJsonWithRetry
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
    ): Result<BidResponse, CLXError> = httpCatching(
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
//            Result.Failure(CLXError(CLXErrorCode.ADS_DISABLED))
        },
        onNoContent = { response, _ ->
            val xStatus = response.headers[HEADER_CLOUDX_STATUS]
            if (xStatus == STATUS_ADS_DISABLED) {
                Result.Failure(CLXError(CLXErrorCode.ADS_DISABLED))
            } else {
                Result.Failure(CLXError(CLXErrorCode.NO_FILL))
            }
        }
    ) {
        val body = withContext(Dispatchers.IO) {
            bidRequest.toString().also { CloudXLogger.d(tag, "Serialized body (${it.length} chars)") }
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