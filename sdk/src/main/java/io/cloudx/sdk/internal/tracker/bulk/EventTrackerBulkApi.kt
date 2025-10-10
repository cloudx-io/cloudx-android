package io.cloudx.sdk.internal.tracker.bulk

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.httpclient.cXExponentialRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Impression tracking API to notify backend when a winning ad was rendered.
 */
internal class EventTrackerBulkApi(
    private val httpClient: HttpClient = CloudXHttpClient(),
) {

    /**
     * Sends a tracking request with encoded impression ID and metadata.
     *
     * @param impressionId - encoded impression data
     * @param campaignId - the ID of the campaign that won
     * @param eventName - name of the event (e.g., "rendered")
     */
    suspend fun send(
        endpointUrl: String,
        items: List<EventAM>
    ): Result<Unit, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, _ -> Unit.toSuccess() },
            onNoContent = { _, _ -> Unit.toSuccess() }
        ) {
            httpClient.post(endpointUrl) {
                setBody(items.toJson())
                contentType(ContentType.Application.Json)
                cXExponentialRetry(3)
            }
        }
    }
}