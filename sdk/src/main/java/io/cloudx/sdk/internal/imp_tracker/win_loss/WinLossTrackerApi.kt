package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CLXError
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
        endpointUrl: String,
        payload: Map<String, Any>,
    ): Result<Unit, CLXError>
}

internal fun WinLossTrackerApi(
    timeoutMillis: Long = 10_000,
    httpClient: HttpClient = CloudXHttpClient(),
): WinLossTrackerApi = WinLossTrackerApiImpl(
    timeoutMillis = timeoutMillis,
    httpClient = httpClient
)
