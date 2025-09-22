package io.cloudx.sdk.internal.cdp

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.httpclient.cloudXConstantRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.withIOContext
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONObject

internal class CdpApiImpl(
    private val endpointUrl: String,
    private val httpClient: HttpClient
) : CdpApi {

    override suspend fun enrich(original: JSONObject): Result<JSONObject, CloudXError> =
        withIOContext {
            httpCatching(
                onOk = { _, json -> Result.Success(JSONObject(json)) },
                onNoContent = { response, _ ->
                    CloudXErrorCode.UNEXPECTED_ERROR.toFailure(
                        message = "Unexpected status: ${response.status}"
                    )
                }
            ) {
                val requestBody = original.toString()
                httpClient.post(endpointUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    cloudXConstantRetry(1)
                }
            }
        }
}
