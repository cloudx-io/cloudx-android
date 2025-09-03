package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.httpclient.CLOUDX_DEFAULT_RETRY_MS
import io.cloudx.sdk.internal.httpclient.HDR_CLOUDX_RETRY_AFTER
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

internal class ConfigApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : ConfigApi {

    private val tag = "ConfigApiImpl"

    override suspend fun invoke(
        appKey: String, configRequest: ConfigRequest
    ): Result<Config, CLXError> {
        val requestBody = withContext(Dispatchers.IO) { configRequest.toJson() }

        Logger.d(tag, buildString {
            appendLine("Config request → $endpointUrl")
            appendLine("  Method: POST")
            appendLine("  AppKey: $appKey")
            appendLine("  Body: $requestBody")
        })

        // First attempt
        val first = makeRequest(appKey, requestBody)

        // Single retry
        val retryDelayMs = shouldRetry(first) ?: return first
        Logger.d(tag, "Retrying config once after ${retryDelayMs}ms")
        delay(retryDelayMs)
        return makeRequest(appKey, requestBody)
    }

    private suspend fun makeRequest(appKey: String, body: String): Result<Config, CLXError> {
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
            // Map exact 4xx/5xx via our handler
            handleResponse(e.response)
        } catch (e: Exception) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, e.message ?: "Network error"))
        }
    }

    private suspend fun handleResponse(response: HttpResponse): Result<Config, CLXError> {
        val responseBody = response.bodyAsText()
        Logger.d(tag, "Config response ← HTTP ${response.status}\n$responseBody")

        return when {
            // 200 OK → parse
            response.status == HttpStatusCode.OK -> {
                when (val parseResult = jsonToConfig(responseBody)) {
                    is Result.Success -> parseResult
                    is Result.Failure -> parseResult
                }
            }

            // 429 → Too many requests; honor Retry-After (ms) via payload
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
                    CLXError(
                        CLXErrorCode.UNKNOWN_ERROR,
                        "Unexpected status: ${response.status}"
                    )
                )
            }
        }
    }

    private fun parseRetryAfter(response: HttpResponse): Long {
        val retryAfterHeader = response.headers[HDR_CLOUDX_RETRY_AFTER]
        return if (retryAfterHeader != null) {
            retryAfterHeader.toLongOrNull()?.let { max(0, it * 1000) } ?: CLOUDX_DEFAULT_RETRY_MS
        } else {
            CLOUDX_DEFAULT_RETRY_MS
        }
    }

    /**
     * Returns retry delay in milliseconds, or null if no retry should be attempted.
     */
    private fun shouldRetry(result: Result<Config, CLXError>): Long? {
        if (result is Result.Success) return null

        val error = (result as? Result.Failure)?.value ?: return null
        return when (error.code) {
            CLXErrorCode.ADS_DISABLED -> null
            CLXErrorCode.CLIENT_ERROR -> null

            // Retry with server-specified delay
            CLXErrorCode.TOO_MANY_REQUESTS ->
                (error.payload as? Long) ?: CLOUDX_DEFAULT_RETRY_MS

            // Retry after 1 second
            CLXErrorCode.SERVER_ERROR,
            CLXErrorCode.NETWORK_ERROR,
            CLXErrorCode.NETWORK_TIMEOUT,
            CLXErrorCode.INVALID_RESPONSE -> CLOUDX_DEFAULT_RETRY_MS

            // Default: no retry
            else -> null
        }
    }
}
