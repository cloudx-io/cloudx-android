package io.cloudx.sdk.internal.config

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXInitializationServer
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.STATUS_SDK_DISABLED
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.httpclient.postJsonWithRetry
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.withIOContext
import io.ktor.client.HttpClient

internal class ConfigApiImpl(
    private val initServer: CloudXInitializationServer,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : ConfigApi {

    private val tag = "ConfigApiImpl"

    override suspend fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CloudXError> = withIOContext {
        httpCatching(
            tag = tag,
            onOk = { _, json -> jsonToConfig(json) },
            onNoContent = { response, _ ->
                val xStatus = response.headers[HEADER_CLOUDX_STATUS]
                when (xStatus) {
                    STATUS_SDK_DISABLED -> Result.Failure(CloudXError(CloudXErrorCode.SDK_DISABLED))
                    STATUS_ADS_DISABLED -> Result.Failure(CloudXError(CloudXErrorCode.ADS_DISABLED))
                    else -> Result.Failure(CloudXError(CloudXErrorCode.NO_FILL))
                }
            }
        ) {
            httpClient.postJsonWithRetry(
                url = initServer.url,
                appKey = appKey,
                jsonBody = configRequest.toJson(),
                timeoutMillis = timeoutMillis,
                retryMax = 1,
                tag = tag
            )
        }
    }
}