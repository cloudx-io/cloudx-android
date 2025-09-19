package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.net.URLEncoder

internal class EventTrackerApiImpl(
    private val timeoutMillis: Long,
    private val httpClient: HttpClient,
) : EventTrackerApi {

    private val tag = "EventTrackingApi"

    override suspend fun send(
        endpointUrl: String,
        encodedData: String,
        campaignId: String,
        eventValue: String,
        eventName: String
    ): Result<Unit, CloudXError> {

        CXLogger.d(tag, buildString {
            appendLine("Sending event tracking  request:")
            appendLine("  Endpoint: $endpointUrl")
            appendLine("  Impression: $encodedData")
            appendLine("  CampaignId: $campaignId")
            appendLine("  EventValue: $eventValue")
            appendLine("  EventName: $eventName")
        })

        CXLogger.i("MainActivity", "Tracking: Sending ${eventName.uppercase()} event")

        return try {
            val response = httpClient.get(endpointUrl) {
                timeout { requestTimeoutMillis = timeoutMillis }
                parameter("impression", URLEncoder.encode(encodedData, Charsets.UTF_8.name()))
                parameter("campaignId", URLEncoder.encode(campaignId, Charsets.UTF_8.name()))
                parameter("eventValue", "1")
                parameter("eventName", eventName)
                parameter("debug", true)

                retry {
                    retryOnServerErrors(maxRetries = 3)
                    constantDelay(millis = 1000)
                }
            }

            CXLogger.d(tag, "Request URL: ${response.call.request.url}")

            val responseBody = response.bodyAsText()
            CXLogger.d(tag, "Tracking response: Status=${response.status}, Body=$responseBody")

            val code = response.status.value
            if (code in 200..299) {
                Result.Success(Unit)
            } else {
                CXLogger.d(tag, "Bad response status: ${response.status}")
                Result.Failure(CloudXError(CloudXErrorCode.SERVER_ERROR))
            }

        } catch (e: Exception) {
            val errStr = "Tracking request failed: ${e.message}"
            CXLogger.e(tag, errStr)
            Result.Failure(CloudXError(CloudXErrorCode.NETWORK_ERROR))
        }
    }
}
