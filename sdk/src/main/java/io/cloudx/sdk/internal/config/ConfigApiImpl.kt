package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.network.BasePostRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ConfigApiImpl(
    endpointUrl: String,
    timeoutMillis: Long,
    httpClient: HttpClient
) : BasePostRequest<Config>(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = httpClient,
    tag = "ConfigApiImpl"
), ConfigApi {

    override suspend fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CLXError> {
        val requestBody = withContext(Dispatchers.IO) { configRequest.toJson() }
        return execute(appKey, requestBody)
    }

    override fun logRequest(appKey: String, body: String) {
        Logger.d("ConfigApiImpl", buildString {
            appendLine("Config request → $endpointUrl")
            appendLine("  Method: POST")
            appendLine("  AppKey: $appKey")
            appendLine("  Body: $body")
        })
    }

    override suspend fun parse(json: String): Result<Config, CLXError> = jsonToConfig(json)
}
