package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class WinLossTrackerApiImpl(
    private val timeoutMillis: Long,
    private val httpClient: HttpClient,
) : WinLossTrackerApi {

    private val tag = "WinLossTrackerApi"

    override suspend fun send(
        endpointUrl: String,
        payload: Map<String, Any>
    ): Result<Unit, CLXError> {
        return try {
            val response = httpClient.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
                timeout { requestTimeoutMillis = timeoutMillis }
                retry {
                    retryOnServerErrors(maxRetries = 1)
                    constantDelay(millis = 1000)
                }
            }

            val code = response.status.value
            if (code in 200..299) {
                Result.Success(Unit)
            } else {
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            }

        } catch (e: Exception) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR))
        }
    }
}