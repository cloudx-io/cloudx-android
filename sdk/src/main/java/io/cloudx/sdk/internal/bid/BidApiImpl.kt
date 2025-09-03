package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CloudXErrorCodes
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.httpclient.HDR_X_CLOUDX_STATUS
import io.cloudx.sdk.internal.httpclient.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.requestTimeoutMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

internal class BidApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : BidApi {

    private val tag = "BidApiImpl"

    override suspend fun invoke(
        appKey: String,
        bidRequest: JSONObject
    ): Result<BidResponse, Error> {

        val requestBody = withContext(Dispatchers.IO) { bidRequest.toString() }
        Logger.d(tag, "Bid request → $endpointUrl\nBody: $requestBody")

        // First attempt
        val firstResult = makeRequest(appKey, requestBody)

        // Check if we should retry
        val retryDelayMs = shouldRetry(firstResult) ?: return firstResult
        Logger.d(tag, "Retrying bid once after ${retryDelayMs}ms")

        // Single retry with appropriate delay
        delay(retryDelayMs)
        return makeRequest(appKey, requestBody)
    }

    private suspend fun makeRequest(appKey: String, body: String): Result<BidResponse, Error> {
        return try {
            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(body)
                requestTimeoutMillis(timeoutMillis)
            }
            handleResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Result.Failure(Error("Request timeout", CloudXErrorCodes.TIMEOUT))
        } catch (e: IOException) {
            Result.Failure(Error("Network error: ${e.message}", CloudXErrorCodes.NETWORK_ERROR))
        } catch (e: ServerResponseException) {
            Result.Failure(
                Error(
                    "Server error: ${e.response.status}",
                    CloudXErrorCodes.SERVER_ERROR
                )
            )
        } catch (e: Exception) {
            Result.Failure(Error("Request failed: ${e.message}", CloudXErrorCodes.UNKNOWN_ERROR))
        }
    }

    private suspend fun handleResponse(response: HttpResponse): Result<BidResponse, Error> {
        val responseBody = response.bodyAsText()
        Logger.d(tag, "Bid response ← HTTP ${response.status}\n$responseBody")

        return when {
            response.status == HttpStatusCode.OK -> {
                when (val parseResult = jsonToBidResponse(responseBody)) {
                    is Result.Success -> {
                        TrackingFieldResolver.setResponseData(
                            parseResult.value.auctionId,
                            JSONObject(responseBody)
                        )
                        parseResult
                    }

                    is Result.Failure -> parseResult
                }
            }

            // 204 No Bid
            response.status == HttpStatusCode.NoContent -> {
                val xStatus = response.headers[HDR_X_CLOUDX_STATUS]
                if (xStatus == STATUS_ADS_DISABLED) {
                    Result.Failure(
                        Error(
                            "Bid request disabled by server-side traffic control",
                            CloudXErrorCodes.ADS_DISABLED
                        )
                    )
                } else {
                    Result.Failure(
                        Error(
                            "No bid available",
                            CloudXErrorCodes.NO_BID_AVAILABLE
                        )
                    )
                }
            }

            // 429
            response.status == HttpStatusCode.TooManyRequests -> {
                val retryAfterMs = parseRetryAfter(response)
                Result.Failure(
                    Error(
                        "Rate limited",
                        CloudXErrorCodes.RATE_LIMITED,
                        retryAfterMs
                    )
                )
            }

            response.status.value in 400..499 -> {
                Result.Failure(
                    Error(
                        "Client error: ${response.status}",
                        CloudXErrorCodes.CLIENT_ERROR
                    )
                )
            }

            response.status.value in 500..599 -> {
                Result.Failure(
                    Error(
                        "Server error: ${response.status}",
                        CloudXErrorCodes.SERVER_ERROR
                    )
                )
            }

            else -> {
                Result.Failure(
                    Error(
                        "Unexpected status: ${response.status}",
                        CloudXErrorCodes.UNKNOWN_ERROR
                    )
                )
            }
        }
    }

    private fun parseRetryAfter(response: HttpResponse): Long {
        val retryAfterHeader = response.headers["Retry-After"]
        return if (retryAfterHeader != null) {
            retryAfterHeader.toLongOrNull()?.let { max(0, it * 1000) } ?: 1000L
        } else {
            1000L
        }
    }

    /**
     * Returns retry delay in milliseconds, or null if no retry should be attempted
     */
    private fun shouldRetry(result: Result<BidResponse, Error>): Long? {
        if (result is Result.Success) return null

        val error = (result as Result.Failure).value
        return when (error.errorCode) {
            // Never retry these
            CloudXErrorCodes.ADS_DISABLED -> null
            CloudXErrorCodes.CLIENT_ERROR -> null

            // Retry with server-specified delay
            CloudXErrorCodes.RATE_LIMITED -> error.retryAfterMs ?: 1000L

            // Retry after 1 second
            CloudXErrorCodes.SERVER_ERROR,
            CloudXErrorCodes.NETWORK_ERROR,
            CloudXErrorCodes.TIMEOUT -> 1000L

            // Default: no retry
            else -> null
        }
    }
}