package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.cXAuthHeader
import io.cloudx.sdk.internal.httpclient.cXExponentialRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONObject

internal class WinLossTrackerApiImpl(
    private val httpClient: HttpClient,
) : WinLossTrackerApi {

    override suspend fun send(
        appKey: String,
        endpointUrl: String,
        payload: Map<String, Any>
    ): Result<Unit, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, _ -> Unit.toSuccess() },
            onNoContent = { _, _ -> Unit.toSuccess() }
        ) {
            val jsonBody = JSONObject(payload).toString()
            httpClient.post(endpointUrl) {
                cXAuthHeader(appKey)
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
                cXExponentialRetry(3)
            }
        }
    }
}