package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * WinLoss API to notify backend when a bid won or lost.
 */
internal fun interface WinLossTrackerApi {

    /**
     * Sends a win/loss notification with dynamic payload.
     *
     * @param endpointUrl - the endpoint URL to send the request to
     * @param payload - dynamic key-value payload data
     */
    suspend fun send(
        appKey: String,
        endpointUrl: String,
        payload: Map<String, Any>,
    ): Result<Unit, CloudXError>
}

internal fun WinLossTrackerApi(
    httpClient: HttpClient = CloudXHttpClient(),
): WinLossTrackerApi = WinLossTrackerApiImpl(
    httpClient = httpClient
)
