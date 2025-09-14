package io.cloudx.sdk.internal.imp_tracker.bulk

import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class EventTrackerBulkApiImpl(
    private val timeoutMillis: Long,
    private val httpClient: HttpClient,
) : EventTrackerBulkApi {

    private val tag = "EventTrackingApi"

    override suspend fun send(
        endpointUrl: String, items: List<EventAM>
    ): Result<Unit, CLXError> {

        CloudXLogger.d(tag, buildString {
            appendLine("Sending event tracking  request:")
            appendLine("  Endpoint: $endpointUrl")
            appendLine("  items: $items")
        })

        CloudXLogger.i("MainActivity", "Tracking: Sending Bulk ${items.count()} events")

        return try {
            val requestJson = items.toJson()
            val response = httpClient.post(endpointUrl) {
                timeout { requestTimeoutMillis = timeoutMillis }
                setBody(requestJson)
                contentType(ContentType.Application.Json)

                retry {
                    retryOnServerErrors(maxRetries = 3)
                    constantDelay(millis = 1000)
                }
            }

            CloudXLogger.d(tag, "Request URL: ${response.call.request.url}")

            val responseBody = response.bodyAsText()
            CloudXLogger.d(tag, "Tracking response: Status=${response.status}, Body=$responseBody")

            val code = response.status.value
            if (code in 200..299) {
                Result.Success(Unit)
            } else {
                CloudXLogger.d(tag, "Bad response status: ${response.status}")
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            }

        } catch (e: Exception) {
            val errStr = "Tracking request failed: ${e.message}"
            CloudXLogger.e(tag, errStr)
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR))
        }
    }
}
