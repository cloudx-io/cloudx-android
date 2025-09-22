package io.cloudx.sdk.internal.tracker.bulk

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.httpclient.cloudXExponentialRetry
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.internal.util.withIOContext
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class EventTrackerBulkApiImpl(
    private val httpClient: HttpClient,
) : EventTrackerBulkApi {

    override suspend fun send(
        endpointUrl: String,
        items: List<EventAM>
    ): Result<Unit, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, _ -> Unit.toSuccess() },
            onNoContent = { response, _ ->
                CloudXErrorCode.UNEXPECTED_ERROR.toFailure(
                    message = "Unexpected status: ${response.status}"
                )
            }
        ) {
            httpClient.post(endpointUrl) {
                setBody(items.toJson())
                contentType(ContentType.Application.Json)
                cloudXExponentialRetry(3)
            }
        }
    }
}
