package io.cloudx.sdk.internal.config

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.httpclient.HttpClient
import io.ktor.client.HttpClient

/**
 * Config API responsible for fetching [Config]; essential for SDK work.
 */
internal fun interface ConfigApi {

    /**
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @param configRequest - Config request data required for SDK initialization/startup (initial configuration request)
     * @return [Config] if api response is successful, otherwise [Error]
     */
    suspend fun invoke(appKey: String, configRequest: ConfigRequest): Result<Config, Error>
}

internal fun ConfigApi(
    endpointUrl: String,
    timeoutMillis: Long = 60_000,
    retryMax: Int = 4,
    httpClient: HttpClient = HttpClient()
): ConfigApi = ConfigApiImpl(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    retryMax = retryMax,
    httpClient = httpClient
)