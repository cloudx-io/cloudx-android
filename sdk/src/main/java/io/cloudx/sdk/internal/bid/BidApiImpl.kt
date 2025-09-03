package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
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

// A sealed class representing the outcome of a single HTTP request + parse
private sealed class BidAttemptResult {
    data class Success(val bidResponse: BidResponse, val raw: String) : BidAttemptResult()
    data class SoftError(val raw: String) : BidAttemptResult()
    data class HardError(val status: Int, val raw: String) : BidAttemptResult()
}

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
        val cyclingResult = cyclingBarrierRetry(
            maxAttemptsPerGroup = 3,
            delayBetweenAttemptsMs = 10_000,
            delayBetweenGroupsMs = 300_000,
            isHardError = { it is BidAttemptResult.HardError },
            isSoftError = { it is BidAttemptResult.SoftError }) { attempt, group ->
            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                requestTimeoutMillis(timeoutMillis)
            }
            val responseBody = response.bodyAsText()
            Logger.d(
                tag,
                msg = "Bid request attempt " +
                        "$attempt (group $group): " +
                        "HTTP ${response.status}\n$responseBody"
            )

            when {
                response.status.value >= 500 -> {
                    BidAttemptResult.HardError(response.status.value, responseBody)
                }

                response.status == HttpStatusCode.OK -> {
                    when (val parsed = jsonToBidResponse(responseBody)) {
                        is Result.Success -> {
                            BidAttemptResult.Success(parsed.value, responseBody)
                        }

                        is Result.Failure -> {
                            BidAttemptResult.SoftError(responseBody)
                        }
                    }
                }

                else -> {
                    Logger.e("MainActivity", "Unexpected response status: ${response.status.value}")
                    BidAttemptResult.HardError(response.status.value, responseBody)
                }
            }
        }

        return when (cyclingResult) {
            is CyclingRetryResult.Success -> {
                val bidResponse = (cyclingResult.value as BidAttemptResult.Success).bidResponse
                val raw = cyclingResult.value.raw
                TrackingFieldResolver.setResponseData(bidResponse.auctionId, JSONObject(raw))
                Result.Success(bidResponse)
            }

            is CyclingRetryResult.SoftError -> {
                val raw = (cyclingResult.value as BidAttemptResult.SoftError).raw
                Logger.e(tag, "Soft error, no bid: $raw")
                Result.Failure(Error("Soft error (no bid)"))
            }

            is CyclingRetryResult.HardError -> {
                Logger.e(tag, "All cycling attempts failed: ${cyclingResult.error.message}")
                Result.Failure(Error(cyclingResult.error.message ?: "Unknown error"))
            }
        }
    }
}
