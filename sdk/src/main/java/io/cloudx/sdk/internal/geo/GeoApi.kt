package io.cloudx.sdk.internal.geo

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * Geo API responsible for fetching user's country code using remote service.
 */
internal interface GeoApi {
    suspend fun fetchGeoHeaders(endpointUrl: String): Result<Map<String, String>, CloudXError>
}

internal fun GeoApi(
    httpClient: HttpClient = CloudXHttpClient()
): GeoApi = GeoApiImpl(
    httpClient = httpClient
)

