package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

internal class WinLossTrackerApiImpl(
    private val timeoutMillis: Long,
    private val httpClient: HttpClient,
) : WinLossTrackerApi {

    private val tag = "WinLossTrackerApi"

    override suspend fun send(
        appKey: String,
        endpointUrl: String,
        payload: Map<String, Any>
    ): Result<Unit, CloudXError> {
        return try {
            val jsonBody = JSONObject(payload).toString()

            println("hop: model - Win/Loss API Request Body: $jsonBody")
            CXLogger.d(tag, "Sending win/loss notification (${jsonBody.length} chars) to: $endpointUrl")

            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
                timeout { requestTimeoutMillis = timeoutMillis }
                retry {
                    retryOnServerErrors(maxRetries = 1)
                    constantDelay(millis = 1000)
                }
            }

            val code = response.status.value
            println("hop: model - Win/Loss API Response Status: $code")

            if (code in 200..299) {
                println("hop: model - Win/Loss API call successful")
                CXLogger.d(tag, "Win/loss notification sent successfully")
                Result.Success(Unit)
            } else {
                println("hop: model - Win/Loss API call failed with status: $code")
                CXLogger.e(tag, "Win/loss notification failed with HTTP status: $code")
                Result.Failure(CloudXError(CloudXErrorCode.SERVER_ERROR))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("hop: model - Win/Loss API call exception: ${e.message}")
            CXLogger.e(tag, "Win/loss notification failed with exception: ${e.message}")
            Result.Failure(CloudXError(CloudXErrorCode.NETWORK_ERROR))
        }
    }
}