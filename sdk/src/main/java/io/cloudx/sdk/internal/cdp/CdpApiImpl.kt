package io.cloudx.sdk.internal.cdp

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.requestTimeoutMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class CdpApiImpl(
    private val endpointUrl: String,
    private val timeoutMillis: Long,
    private val httpClient: HttpClient
) : CdpApi {

    private val tag = "CdpApi"

    override suspend fun enrich(original: JSONObject): Result<JSONObject, CLXError> {
        val requestBody = withContext(Dispatchers.IO) { original.toString() }

        CloudXLogger.d(tag, buildString {
            appendLine("Calling CDP Lambda:")
            appendLine("  Endpoint: $endpointUrl")
            appendLine("  Request: $requestBody")
        })

        return try {
            val response = httpClient.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                requestTimeoutMillis(timeoutMillis)
            }

            val responseBody = response.bodyAsText()
            CloudXLogger.d(tag, buildString {
                appendLine("CDP Response:")
                appendLine("  Status: ${response.status}")
                appendLine("  Body: $responseBody")
            })

            if (response.status == HttpStatusCode.OK) {
                Result.Success(JSONObject(responseBody))
            } else {
                Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR, "CDP returned error status: ${response.status}"))
            }

        } catch (e: Exception) {
            Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, "CDP Lambda call failed: ${e.message}"))
        }
    }
}
