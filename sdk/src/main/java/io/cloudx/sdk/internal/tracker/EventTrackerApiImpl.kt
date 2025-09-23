package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.cXExponentialRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.net.URLEncoder

internal class EventTrackerApiImpl(
    private val httpClient: HttpClient,
) : EventTrackerApi {

    override suspend fun send(
        endpointUrl: String,
        encodedData: String,
        campaignId: String,
        eventValue: String,
        eventName: String
    ): Result<Unit, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, _ -> Unit.toSuccess() },
            onNoContent = { _, _ -> Unit.toSuccess() }
        ) {
            httpClient.get(endpointUrl) {
                parameter("impression", URLEncoder.encode(encodedData, Charsets.UTF_8.name()))
                parameter("campaignId", URLEncoder.encode(campaignId, Charsets.UTF_8.name()))
                parameter("eventValue", "1")
                parameter("eventName", eventName)
                cXExponentialRetry(retryMax = 3)
            }
        }
    }
}
