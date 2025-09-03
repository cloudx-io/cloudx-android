package io.cloudx.sdk.internal.imp_tracker

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.network.httpclient.CloudXHttpClient
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
    ): Result<Unit, CLXError>
}

internal fun EventTrackerApi(
    timeoutMillis: Long = 10_000,
    httpClient: HttpClient = CloudXHttpClient(),
): EventTrackerApi = EventTrackerApiImpl(
    timeoutMillis = timeoutMillis,
    httpClient = httpClient
)
