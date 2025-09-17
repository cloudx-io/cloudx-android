package io.cloudx.sdk.internal.cdp

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.httpclient.CloudXHttpClient
import io.cloudx.sdk.internal.util.Result
import org.json.JSONObject

/**
 * Sends bid payload to CDP Lambda and expects enriched payload back.
 */
internal interface CdpApi {
    suspend fun enrich(original: JSONObject): Result<JSONObject, CloudXError>
}

internal fun CdpApi(endpointUrl: String, timeoutMillis: Long): CdpApi = CdpApiImpl(
    endpointUrl = endpointUrl,
    timeoutMillis = timeoutMillis,
    httpClient = CloudXHttpClient()
)
