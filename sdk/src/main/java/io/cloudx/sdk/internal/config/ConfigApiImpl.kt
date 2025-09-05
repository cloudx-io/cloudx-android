package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.STATUS_SDK_DISABLED
import io.cloudx.sdk.internal.network.httpCatching
import io.cloudx.sdk.internal.network.postJsonWithRetry
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ConfigApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : ConfigApi {

    private val tag = "ConfigApiImpl"

    override suspend fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CLXError> = httpCatching(
        tag = tag,
        onOk = { json -> jsonToConfig(json) },
        onNoContent = { _, _ -> Result.Failure(CLXError(CLXErrorCode.NO_FILL)) }
    ) {
        val body = withContext(Dispatchers.IO) {
            configRequest.toJson().also { CloudXLogger.d(tag, "Serialized body (${it.length} chars)") }
        }
        httpClient.postJsonWithRetry(
            url = endpointUrl,
            appKey = appKey,
            jsonBody = body,
            timeoutMillis = timeoutMillis,
            retryMax = 1,
            tag = tag
        )
    }
}