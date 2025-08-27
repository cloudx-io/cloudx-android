package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CloudXErrorCodes
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.httpclient.HDR_X_CLOUDX_STATUS
import io.cloudx.sdk.internal.httpclient.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.kill_switch.KillSwitch
import io.cloudx.sdk.internal.requestTimeoutMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
        appKey: String, bidRequest: JSONObject
    ): Result<BidResponse, Error> {

        val requestBody = withContext(Dispatchers.IO) { bidRequest.toString() }
        Logger.d(
            tag, "Attempting bid request:\n  Endpoint: $endpointUrl\n  Request Body: $requestBody"
        )

        return try {
            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                requestTimeoutMillis(timeoutMillis)
            }
            val responseBody = response.bodyAsText()
            Logger.d(
                tag,
                "Received bid response: HTTP ${response.status}\n$responseBody"
            )

            when {
                response.status == HttpStatusCode.OK -> {
                    when (val bidResponseResult = jsonToBidResponse(responseBody)) {
                        is Result.Failure -> {
                            val error = bidResponseResult.value
                            Logger.e(tag, "Failed to parse bid response: ${error.description}")
                            Result.Failure(error)
                        }

                        is Result.Success -> {
                            val auctionId = bidResponseResult.value.auctionId
                            TrackingFieldResolver.setResponseData(auctionId, JSONObject(responseBody))
                            bidResponseResult
                        }
                    }
                }
                
                response.status == HttpStatusCode.NoContent -> {
                    val xStatus = response.headers[HDR_X_CLOUDX_STATUS]
                    if (xStatus == STATUS_ADS_DISABLED) {
                        Logger.w(tag, "Ads disabled by traffic control (ADS_DISABLED) - no retries")
                        KillSwitch.sourceErrorCode = CloudXErrorCodes.ADS_DISABLED
                        Result.Failure(Error("Bid request disabled by server-side traffic control", CloudXErrorCodes.ADS_DISABLED))
                    } else {
                        // Regular no-bid response
                        val errStr = "No bid available (204)"
                        Logger.d(tag, errStr)
                        Result.Failure(Error(errStr))
                    }
                }
                
                else -> {
                    val errStr = "Bad response status: ${response.status}"
                    Logger.e(tag, errStr)
                    Result.Failure(Error(errStr))
                }
            }
        } catch (e: Exception) {
            val errStr = "Request failed: ${e.message}"
            Logger.e(tag, errStr)
            Result.Failure(Error(errStr))
        }
    }
}