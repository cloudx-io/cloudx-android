package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * Impression tracking API to notify backend when a winning ad was rendered.
 */
internal fun interface EventTrackerApi {

    /**
     * Sends a tracking request with encoded impression ID and metadata.
     *
     * @param impressionId - encoded impression data
     * @param campaignId - the ID of the campaign that won
     * @param eventName - name of the event (e.g., "rendered")
     */
    suspend fun send(
        endpointUrl: String,
        encodedData: String,
        campaignId: String,
        eventValue: String,
        eventName: String,
    ): Result<Unit, CloudXError>
}

internal fun EventTrackerApi(
    httpClient: HttpClient = CloudXHttpClient(),
): EventTrackerApi = EventTrackerApiImpl(
    httpClient = httpClient
)
