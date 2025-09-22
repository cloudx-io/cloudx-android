package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.httpclient.cloudXExponentialRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.net.URLEncoder

internal class EventTrackerApiImpl(
    private val httpClient: HttpClient,
) : EventTrackerApi {

    private val TAG = "EventTrackingApi"

    override suspend fun send(
        endpointUrl: String,
        encodedData: String,
        campaignId: String,
        eventValue: String,
        eventName: String
    ): Result<Unit, CloudXError> = withIOContext {
        httpCatching(
            tag = TAG,
            onOk = { _, _ -> Unit.toSuccess() },
            onNoContent = { response, _ ->
                CloudXErrorCode.UNEXPECTED_ERROR.toFailure(
                    message = "Unexpected status: ${response.status}"
                )
            }
        ) {
            httpClient.get(endpointUrl) {
                parameter("impression", URLEncoder.encode(encodedData, Charsets.UTF_8.name()))
                parameter("campaignId", URLEncoder.encode(campaignId, Charsets.UTF_8.name()))
                parameter("eventValue", "1")
                parameter("eventName", eventName)
                parameter("debug", true)
                cloudXExponentialRetry(retryMax = 3)
            }
        }
    }
}
