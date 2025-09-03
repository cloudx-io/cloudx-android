package io.cloudx.sdk.internal.network

import io.cloudx.sdk.internal.network.httpclient.CLOUDX_DEFAULT_RETRY_MS
import io.cloudx.sdk.internal.network.httpclient.HDR_CLOUDX_RETRY_AFTER

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.requestTimeoutMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

/**
 * POST-JSON base with:
 * - Bearer auth
 * - requestTimeoutMillis(timeoutMillis)
 * - status→error mapping
 * - single retry policy
 *
 * Subclasses must implement:
 *  - parse(json)
 *
 * Subclasses may override:
 *  - mapSpecialStatuses(...) for custom statuses (e.g., 204)
 *  - onParsedSuccess(...) for side effects
 *  - logRequest(...) to customize request logs
 *  - retryableCodes()/neverRetryCodes() to tweak retry policy
 */
internal abstract class BasePostRequest<T>(
    protected val endpointUrl: String,
    protected val timeoutMillis: Long,
    protected val httpClient: HttpClient,
    private val tag: String
) {

    /** Parse 200 OK body into domain object. Return parser's Result as-is. */
    protected abstract suspend fun parse(json: String): Result<T, CLXError>

    /** Hook for custom status handling (return non-null to short-circuit default mapping). */
    protected open fun mapSpecialStatuses(
        response: HttpResponse,
        body: String
    ): Result<T, CLXError>? = null

    /** Hook after a successful parse. */
    protected open fun onParsedSuccess(parsed: T, rawBody: String, response: HttpResponse) {}

    /** Default request log; subclasses can override for custom formatting. */
    protected open fun logRequest(appKey: String, body: String) {
        Logger.d(tag, "Request → $endpointUrl\nBody: $body")
    }

    /** Default response log. */
    protected open fun logResponse(response: HttpResponse, body: String) {
        Logger.d(tag, "Response ← HTTP ${response.status}\n$body")
    }

    /** Default non-retryable codes. */
    protected open fun neverRetryCodes(): Set<CLXErrorCode> =
        setOf(CLXErrorCode.ADS_DISABLED, CLXErrorCode.CLIENT_ERROR)

    /** Default retryable codes (subclasses may extend). */
    protected open fun retryableCodes(): Set<CLXErrorCode> =
        setOf(
            CLXErrorCode.SERVER_ERROR,
            CLXErrorCode.NETWORK_ERROR,
            CLXErrorCode.NETWORK_TIMEOUT,
            CLXErrorCode.INVALID_RESPONSE
        )

    /** Public entry: performs POST, then single retry if policy asks for it. */
    protected suspend fun execute(appKey: String, body: String): Result<T, CLXError> {
        logRequest(appKey, body)

        val first = makeRequest(appKey, body)
        val retryDelayMs = shouldRetry(first) ?: return first

        Logger.d(tag, "Retrying once after ${retryDelayMs}ms")
        delay(retryDelayMs)

        return makeRequest(appKey, body)
    }

    private suspend fun makeRequest(appKey: String, body: String): Result<T, CLXError> {
        return try {
            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(body)
                requestTimeoutMillis(timeoutMillis)
            }
            handleResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_TIMEOUT))
        } catch (e: IOException) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR))
        } catch (e: ServerResponseException) {
            // Map by status in a consistent way
            handleResponse(e.response)
        } catch (e: Exception) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, e.message ?: "Network error"))
        }
    }

    private suspend fun handleResponse(response: HttpResponse): Result<T, CLXError> {
        val responseBody = response.bodyAsText()
        logResponse(response, responseBody)

        // Allow subclass to intercept special statuses
        mapSpecialStatuses(response, responseBody)?.let { return it }

        return when {
            response.status == HttpStatusCode.OK -> {
                // Return parser's result as-is (success or failure)
                when (val parsed = parse(responseBody)) {
                    is Result.Success -> {
                        onParsedSuccess(parsed.value, responseBody, response)
                        parsed
                    }

                    is Result.Failure -> parsed
                }
            }

            response.status == HttpStatusCode.TooManyRequests -> {
                val retryAfterMs = parseRetryAfter(response)
                Result.Failure(CLXError(CLXErrorCode.TOO_MANY_REQUESTS, payload = retryAfterMs))
            }

            response.status.value in 400..499 -> {
                Result.Failure(CLXError(CLXErrorCode.CLIENT_ERROR, "HTTP ${response.status.value}"))
            }

            response.status.value in 500..599 -> {
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            }

            else -> {
                Result.Failure(
                    CLXError(CLXErrorCode.UNKNOWN_ERROR, "Unexpected status: ${response.status}")
                )
            }
        }
    }

    private fun parseRetryAfter(response: HttpResponse): Long {
        val raw = response.headers[HDR_CLOUDX_RETRY_AFTER]
        return if (raw != null) {
            raw.toLongOrNull()?.let { max(0, it * 1000) } ?: CLOUDX_DEFAULT_RETRY_MS
        } else {
            CLOUDX_DEFAULT_RETRY_MS
        }
    }

    /** Centralized single-retry policy; subclasses can tweak via retryable/never sets. */
    private fun shouldRetry(result: Result<T, CLXError>): Long? {
        val error = (result as? Result.Failure)?.value ?: return null

        // Never retry
        if (error.code in neverRetryCodes()) return null

        // Special-case 429 with payload delay
        if (error.code == CLXErrorCode.TOO_MANY_REQUESTS) {
            return (error.payload as? Long) ?: CLOUDX_DEFAULT_RETRY_MS
        }

        // Default retryable codes
        if (error.code in retryableCodes()) {
            return CLOUDX_DEFAULT_RETRY_MS
        }

        // Otherwise don't retry
        return null
    }
}
