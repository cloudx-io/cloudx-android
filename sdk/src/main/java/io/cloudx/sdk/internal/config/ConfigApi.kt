package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.ktor.client.HttpClient

/**
 * Config API responsible for fetching [Config]; essential for SDK work.
 */
internal fun interface ConfigApi {

    /**
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @param configRequest - Config request data required for SDK initialization/startup (initial configuration request)
     * @return [Config] if api response is successful, otherwise [CLXError]
     */
    suspend fun invoke(appKey: String, configRequest: ConfigRequest): Result<Config, CLXError>
}

internal fun ConfigApi(
    endpointUrl: String,
    timeoutMillis: Long = 60_000,
    httpClient: HttpClient = CloudXHttpClient()
): ConfigApi = ConfigApiImpl(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = httpClient
)