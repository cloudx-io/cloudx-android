package io.cloudx.sdk.internal.config

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXInitializationServer
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.cloudx.sdk.internal.STATUS_SDK_DISABLED
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.httpclient.httpCatching
import io.cloudx.sdk.internal.httpclient.postJsonWithRetry
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.withIOContext
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient

/**
 * Config API responsible for fetching [Config]; essential for SDK work.
 */
internal class ConfigApi(
    private val initServer: CloudXInitializationServer,
    private val timeoutMillis: Long = 60_000,
    private val httpClient: HttpClient = CloudXHttpClient()
) {

    /**
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @param configRequest - Config request data required for SDK initialization/startup (initial configuration request)
     * @return [Config] if api response is successful, otherwise [CloudXError]
     */
    suspend operator fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CloudXError> = withIOContext {
        httpCatching(
            onOk = { _, json -> jsonToConfig(json) },
            onNoContent = { response, _ ->
                val xStatus = response.headers[HEADER_CLOUDX_STATUS]
                when (xStatus) {
                    STATUS_SDK_DISABLED -> CloudXErrorCode.SDK_DISABLED.toFailure()
                    STATUS_ADS_DISABLED -> CloudXErrorCode.ADS_DISABLED.toFailure()
                    else -> CloudXErrorCode.NO_FILL.toFailure()
                }
            }
        ) {
            httpClient.postJsonWithRetry(
                url = initServer.url,
                appKey = appKey,
                jsonBody = configRequest.toJson(),
                timeoutMillis = timeoutMillis,
                retryMax = 1,
            )
        }
    }
}
