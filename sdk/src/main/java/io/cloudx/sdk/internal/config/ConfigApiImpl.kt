package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

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
        CloudXLogger.d(tag, buildString {
            appendLine("Attempting config request:")
            appendLine("  Endpoint: $endpointUrl")
            appendLine("  AppKey: $appKey")
            appendLine("  Request Body: ${configRequest.toJson()}")
        })

        val isStatic = endpointUrl.contains("type=static")

        return try {
            val response = if (isStatic) {
                httpClient.get(endpointUrl) {
                    headers {
                        append("Authorization", "Bearer $appKey")
                    }

                    timeout {
                        requestTimeoutMillis = timeoutMillis
                    }

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
            } else {
                httpClient.post(endpointUrl) {
                    headers {
                        append("Authorization", "Bearer $appKey")
                    }

                    contentType(ContentType.Application.Json)
                    setBody(configRequest.toJson())

                    timeout {
                        requestTimeoutMillis = timeoutMillis
                    }

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
            }

            val responseBody = response.bodyAsText()
            CloudXLogger.d(tag, buildString {
                appendLine("Received response:")
                appendLine("  Status: ${response.status}")
                appendLine("  Body: $responseBody")
            })

            if (response.status == HttpStatusCode.OK) {
                when (val result = jsonToConfig(responseBody)) {
                    is Result.Success -> Result.Success(result.value)
                    is Result.Failure -> {
                        CloudXLogger.e(tag, "Failed to parse config: ${result.value.effectiveMessage}")
                        Result.Failure(result.value)
                    }
                }
            } else {
                val errStr = "Bad response status: ${response.status}"
                CloudXLogger.e(tag, errStr)
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            }

        } catch (e: Exception) {
            val errStr = "Request failed: ${e.message}"
            CloudXLogger.e(tag, errStr)
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR))
        }
    }
}