package io.cloudx.sdk.internal.imp_tracker.bulk

import io.cloudx.sdk.internal.CLXError
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
    ): Result<Unit, CLXError>
}

internal fun EventTrackerBulkApi(
    timeoutMillis: Long = 10_000,
    httpClient: HttpClient = io.cloudx.sdk.internal.httpclient.CloudXHttpClient(),
): EventTrackerBulkApi = EventTrackerBulkApiImpl(
    timeoutMillis = timeoutMillis,
    httpClient = httpClient
)