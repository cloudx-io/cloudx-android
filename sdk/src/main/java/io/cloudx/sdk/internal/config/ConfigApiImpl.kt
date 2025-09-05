package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.CLOUDX_DEFAULT_RETRY_MS
import io.cloudx.sdk.internal.requestTimeoutMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.retry
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal class ConfigApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val retryMax: Int,
    private val httpClient: HttpClient
) : ConfigApi {

    private val tag = "ConfigApiImpl"

    override suspend fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CLXError> {

        val requestBody = withContext(Dispatchers.IO) { configRequest.toJson() }
        CloudXLogger.d(tag, "Config request → $endpointUrl\nBody: $requestBody")

        return makeRequest(appKey, requestBody)
    }

    private suspend fun makeRequest(
        appKey: String,
        body: String
    ): Result<Config, CLXError> {
        return try {
            val response = httpClient.post(endpointUrl) {
                headers { append("Authorization", "Bearer $appKey") }
                contentType(ContentType.Application.Json)
                setBody(body)
                requestTimeoutMillis(timeoutMillis)
                retry {
                    maxRetries = retryMax
                    retryOnExceptionOrServerErrors()
                    retryIf { _, response ->
                        response.status.value == 429 // Too Many Requests
                    }
                    constantDelay(
                        millis = CLOUDX_DEFAULT_RETRY_MS,
                        randomizationMs = 1000L,
                        respectRetryAfterHeader = true
                    )
                }
            }
            handleResponse(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_TIMEOUT, cause = e))
        } catch (e: IOException) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, cause = e))
        } catch (e: ServerResponseException) {
            handleResponse(e.response)
        } catch (e: Exception) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, e.message, e))
        }
    }

    private suspend fun handleResponse(response: HttpResponse): Result<Config, CLXError> {
        val responseBody = response.bodyAsText()
        CloudXLogger.d(tag, "Config response ← HTTP ${response.status}\n$responseBody")

        return when {
            response.status == HttpStatusCode.OK -> {
                when (val parseResult = jsonToConfig(responseBody)) {
                    is Result.Success -> parseResult
                    is Result.Failure -> parseResult
                }
            }

            // 429
            response.status == HttpStatusCode.TooManyRequests -> {
                Result.Failure(CLXError(CLXErrorCode.TOO_MANY_REQUESTS))
            }

            response.status.value in 400..499 -> {
                Result.Failure(CLXError(CLXErrorCode.CLIENT_ERROR))
            }

            response.status.value in 500..599 -> {
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            }

            else -> {
                Result.Failure(
                    CLXError(
                        CLXErrorCode.UNEXPECTED_ERROR,
                        "Unexpected status: ${response.status}"
                    )
                )
            }
        }
    }
}