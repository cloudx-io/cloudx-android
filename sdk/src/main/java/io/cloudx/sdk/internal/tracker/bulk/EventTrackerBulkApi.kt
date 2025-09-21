package io.cloudx.sdk.internal.tracker.bulk

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * Impression tracking API to notify backend when a winning ad was rendered.
 */
internal fun interface EventTrackerBulkApi {

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
    ): Result<Unit, CloudXError>
}

internal fun EventTrackerBulkApi(
    httpClient: HttpClient = CloudXHttpClient(),
): EventTrackerBulkApi = EventTrackerBulkApiImpl(
    httpClient = httpClient
)