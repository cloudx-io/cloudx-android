package io.cloudx.sdk.internal.config

import io.cloudx.sdk.CloudXInitializationServer
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import io.ktor.client.HttpClient

/**
 * Config API responsible for fetching [Config]; essential for SDK work.
 */
internal fun interface ConfigApi {

    /**
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @param configRequest - Config request data required for SDK initialization/startup (initial configuration request)
     * @return [Config] if api response is successful, otherwise [CloudXError]
     */
    suspend fun invoke(appKey: String, configRequest: ConfigRequest): Result<Config, CloudXError>
}

internal fun ConfigApi(
    initServer: CloudXInitializationServer,
    timeoutMillis: Long = 60_000,
    httpClient: HttpClient = CloudXHttpClient()
): ConfigApi = ConfigApiImpl(
    initServer = initServer,
    timeoutMillis = timeoutMillis,
    httpClient = httpClient
)