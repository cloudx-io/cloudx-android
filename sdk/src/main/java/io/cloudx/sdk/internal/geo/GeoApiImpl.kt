package io.cloudx.sdk.internal.geo

import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

internal class GeoApiImpl(
    private val timeoutMillis: Long,
    private val retryMax: Int,
    private val httpClient: HttpClient
) : GeoApi {

    private val tag = "GeoApiImpl"

    override suspend fun fetchGeoHeaders(endpointUrl: String): Result<Map<String, String>, CLXError> {
        CloudXLogger.d(tag, "Fetching geo headers from $endpointUrl")

        return try {
            val response: HttpResponse = httpClient.head(endpointUrl) {
                timeout { requestTimeoutMillis = timeoutMillis }
                retry {
                    maxRetries = retryMax
                    exponentialDelay()
                    retryOnException(retryOnTimeout = true)
                    retryIf { _, httpResponse ->
                        httpResponse.status.value in 500..599 ||
                                httpResponse.status == HttpStatusCode.RequestTimeout ||
                                httpResponse.status == HttpStatusCode.NotFound
                    }
                }
            }

            // Return ALL response headers as a map
            val headersMap = response.headers.entries()
                .associate { (key, values) -> key to values.joinToString(",") }

            CloudXLogger.d(tag, "Fetched headers: $headersMap")

            if (response.status == HttpStatusCode.OK && headersMap.isNotEmpty()) {
                Result.Success(headersMap)
            } else {
                CloudXLogger.d(tag, "No headers found in response")
                Result.Failure(CLXError(CLXErrorCode.INVALID_RESPONSE))
            }
        } catch (e: Exception) {
            CloudXLogger.e(tag, "Geo fetch failed: ${e.message}")
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR))
        }
    }
}
