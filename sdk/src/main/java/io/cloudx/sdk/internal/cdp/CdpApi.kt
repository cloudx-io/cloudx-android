package io.cloudx.sdk.internal.cdp

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.network.httpclient.CloudXHttpClient
import org.json.JSONObject

/**
 * Sends bid payload to CDP Lambda and expects enriched payload back.
 */
internal interface CdpApi {
    suspend fun enrich(original: JSONObject): Result<JSONObject, CLXError>
}

internal fun CdpApi(endpointUrl: String, timeoutMillis: Long): CdpApi = CdpApiImpl(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = CloudXHttpClient()
)
