package io.cloudx.sdk.internal.geo

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.httpclient.cXConstantRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.head

internal class GeoApiImpl(
    private val httpClient: HttpClient
) : GeoApi {

    private val TAG = "GeoApi"

    override suspend fun fetchGeoHeaders(endpointUrl: String): Result<Map<String, String>, CloudXError> =
        withIOContext {
            httpCatching(
                onOk = { response, _ ->
                    val headersMap = response.headers.entries()
                        .associate { (key, values) -> key to values.joinToString(",") }
                    if (headersMap.isNotEmpty()) {
                        headersMap.toSuccess()
                    } else {
                        val message = "No headers found in response"
                        CXLogger.w(TAG, message)
                        CloudXErrorCode.INVALID_RESPONSE.toFailure(message = message)
                    }
                },
                onNoContent = { response, _ ->
                    CloudXErrorCode.UNEXPECTED_ERROR.toFailure(
                        message = "Unexpected status: ${response.status}"
                    )
                }
            ) {
                httpClient.head(endpointUrl) {
                    cXConstantRetry(1)
                }
            }
        }
}
