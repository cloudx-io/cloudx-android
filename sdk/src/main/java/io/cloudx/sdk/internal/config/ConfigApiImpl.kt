package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.CloudXErrorCodes
import io.cloudx.sdk.internal.ERROR_CODE_GENERAL
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.httpclient.HDR_X_CLOUDX_STATUS
import io.cloudx.sdk.internal.httpclient.STATUS_SDK_DISABLED
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
    ): Result<Config, Error> {
        Logger.d(tag, buildString {
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

            if (response.status == HttpStatusCode.NoContent) {
                val xStatus = response.headers[HDR_X_CLOUDX_STATUS]
                if (xStatus == STATUS_SDK_DISABLED) {
                    Logger.e(tag, "Init disabled by server (traffic control).")
                    return Result.Failure(
                        Error(
                            description = "SDK disabled by server (traffic control)",
                            errorCode = CloudXErrorCodes.INIT_SDK_DISABLED
                        )
                    )
                } else {
                    return Result.Failure(
                        Error(
                            description = "Init returned unexpected 204 No Content",
                            errorCode = ERROR_CODE_GENERAL
                        )
                    )
                }
            }

            val responseBody = response.bodyAsText()
            Logger.d(tag, buildString {
                appendLine("Received response:")
                appendLine("  Status: ${response.status}")
                appendLine("  Body: $responseBody")
            })

            if (response.status == HttpStatusCode.OK) {
                when (val result = jsonToConfig(responseBody)) {
                    is Result.Success -> Result.Success(result.value)
                    is Result.Failure -> {
                        Logger.e(tag, "Failed to parse config: ${result.value.description}")
                        Result.Failure(result.value)
                    }
                }
            } else {
                val errStr = "Bad response status: ${response.status}"
                Logger.e(tag, errStr)
                Result.Failure(Error(errStr))
            }

        } catch (e: Exception) {
            val errStr = "Request failed: ${e.message}"
            Logger.e(tag, errStr)
            Result.Failure(Error(errStr))
        }
    }
}