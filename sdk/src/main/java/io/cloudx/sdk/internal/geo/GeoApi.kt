package io.cloudx.sdk.internal.geo

import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * Geo API responsible for fetching user's country code using remote service.
 */
internal interface GeoApi {
    suspend fun fetchGeoHeaders(endpointUrl: String): Result<Map<String, String>, CLXError>
}

internal fun GeoApi(
    timeoutMillis: Long = 10_000,
    retryMax: Int = 3,
    httpClient: HttpClient = CloudXHttpClient()
): GeoApi = GeoApiImpl(
    timeoutMillis = timeoutMillis,
    retryMax = retryMax,
    httpClient = httpClient
)

