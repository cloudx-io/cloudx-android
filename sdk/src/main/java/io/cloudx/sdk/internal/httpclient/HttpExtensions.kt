package io.cloudx.sdk.internal.httpclient

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CLOUDX_DEFAULT_RETRY_MS
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.toFailure
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal fun HttpRequestBuilder.requestTimeoutMs(millis: Long) =
    timeout { requestTimeoutMillis = millis }

internal fun HttpMessageBuilder.cXAuthHeader(appKey: String) {
    headers { append("Authorization", "Bearer $appKey") }
}

internal fun HttpRequestRetry.Configuration.cXRetry(retryMax: Int) {
    maxRetries = retryMax
    retryOnExceptionOrServerErrors()
    retryIf { _, resp -> resp.status == HttpStatusCode.TooManyRequests }
}

internal fun HttpRequestBuilder.cXConstantRetry(retryMax: Int) {
    retry {
        cXRetry(retryMax)
        constantDelay(
            millis = CLOUDX_DEFAULT_RETRY_MS,
            randomizationMs = 1000L,
            respectRetryAfterHeader = true
        )
    }
}

internal fun HttpRequestBuilder.cXExponentialRetry(retryMax: Int) {
    retry {
        cXRetry(retryMax)
        exponentialDelay()
    }
}

/** POST JSON with common headers, timeout and retry policy. */
internal suspend fun HttpClient.postJsonWithRetry(
    url: String,
    appKey: String,
    jsonBody: String,
    timeoutMillis: Long,
    retryMax: Int,
): HttpResponse {
    return this.post(url) {
        cXAuthHeader(appKey)
        contentType(ContentType.Application.Json)
        setBody(jsonBody)
        requestTimeoutMs(timeoutMillis)
        cXConstantRetry(retryMax)
    }
}

/** Common try/catch around an HTTP call – maps exceptions to CLXError. */
internal suspend inline fun <T> httpCatching(
    crossinline block: suspend () -> Result<T, CloudXError>
): Result<T, CloudXError> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        CloudXErrorCode.NETWORK_TIMEOUT.toFailure(e.message, e)
    } catch (e: IOException) {
        CloudXErrorCode.NETWORK_ERROR.toFailure(e.message, e)
    } catch (e: ServerResponseException) {
        CloudXErrorCode.SERVER_ERROR.toFailure(e.message, e)
    } catch (e: Exception) {
        CloudXErrorCode.NETWORK_ERROR.toFailure(e.message, e)
    }

/** Map a response to Result using pluggable OK/NoContent handlers. */
internal suspend inline fun <T> HttpResponse.mapToResult(
    crossinline onOk: suspend (resp: HttpResponse, body: String) -> Result<T, CloudXError>,
    crossinline onNoContent: suspend (resp: HttpResponse, body: String) -> Result<T, CloudXError>
): Result<T, CloudXError> {
    val body = bodyAsText()
    return when (status) {
        HttpStatusCode.OK -> onOk(this, body)
        HttpStatusCode.NoContent -> onNoContent(this, body)
        HttpStatusCode.TooManyRequests -> CloudXErrorCode.TOO_MANY_REQUESTS.toFailure()
        else -> when (status.value) {
            in 400..499 -> CloudXErrorCode.CLIENT_ERROR.toFailure()
            in 500..599 -> CloudXErrorCode.SERVER_ERROR.toFailure()
            else -> CloudXErrorCode.UNEXPECTED_ERROR.toFailure("Unexpected status: $status")
        }
    }
}

/** GET with common headers, timeout and retry policy. */
internal suspend fun HttpClient.getWithRetry(
    url: String,
    appKey: String,
    timeoutMillis: Long,
    retryMax: Int,
): HttpResponse {
    return this.get(url) {
        headers { append("Authorization", "Bearer $appKey") }
        requestTimeoutMs(timeoutMillis)
        cXConstantRetry(retryMax)
    }
}

/** Convenience wrapper that combines httpCatching + postJsonWithRetry + mapToResult */
internal suspend inline fun <T> httpCatching(
    crossinline onOk: suspend (resp: HttpResponse, body: String) -> Result<T, CloudXError>,
    crossinline onNoContent: suspend (resp: HttpResponse, body: String) -> Result<T, CloudXError>,
    crossinline block: suspend () -> HttpResponse
): Result<T, CloudXError> = httpCatching {
    block().mapToResult(onOk, onNoContent)
}