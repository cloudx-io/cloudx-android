package io.cloudx.sdk.internal.network

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.retry
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** POST JSON with common headers, timeout and retry policy. */
internal suspend fun HttpClient.postJsonWithRetry(
    url: String,
    appKey: String,
    jsonBody: String,
    timeoutMillis: Long,
    retryMax: Int,
    tag: String,
    extraRetryPredicate: (HttpResponse) -> Boolean = { it.status == HttpStatusCode.TooManyRequests }
): HttpResponse {
    CloudXLogger.d(tag, "HTTP → POST $url\nBody: $jsonBody")
    return this.post(url) {
        headers { append("Authorization", "Bearer $appKey") }
        contentType(ContentType.Application.Json)
        setBody(jsonBody)
        requestTimeoutMillis(timeoutMillis)
        retry {
            maxRetries = retryMax
            retryOnExceptionOrServerErrors()
            retryIf { _, resp -> extraRetryPredicate(resp) }
            constantDelay(
                millis = CLOUDX_DEFAULT_RETRY_MS,
                randomizationMs = 1000L,
                respectRetryAfterHeader = true
            )
        }
    }
}

/** Common try/catch around an HTTP call – maps exceptions to CLXError. */
internal suspend inline fun <T> httpCatching(
    crossinline block: suspend () -> Result<T, CLXError>
): Result<T, CLXError> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Result.Failure(CLXError(CLXErrorCode.NETWORK_TIMEOUT, cause = e))
    } catch (e: IOException) {
        Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, cause = e))
    } catch (e: ServerResponseException) {
        Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR, cause = e))
    } catch (e: Exception) {
        Result.Failure(CLXError(CLXErrorCode.NETWORK_ERROR, e.message, e))
    }

/** Map a response to Result using pluggable OK/NoContent handlers. */
internal suspend inline fun <T> HttpResponse.mapToResult(
    tag: String,
    crossinline onOk: suspend (body: String) -> Result<T, CLXError>,
    crossinline onNoContent: suspend (resp: HttpResponse, body: String) -> Result<T, CLXError>
): Result<T, CLXError> {
    val body = bodyAsText()
    CloudXLogger.d(tag, "HTTP ← ${status.value}\n$body")
    return when (status) {
        HttpStatusCode.OK -> onOk(body)
        HttpStatusCode.NoContent -> onNoContent(this, body)
        HttpStatusCode.TooManyRequests -> Result.Failure(CLXError(CLXErrorCode.TOO_MANY_REQUESTS))
        else -> when (status.value) {
            in 400..499 -> Result.Failure(CLXError(CLXErrorCode.CLIENT_ERROR))
            in 500..599 -> Result.Failure(CLXError(CLXErrorCode.SERVER_ERROR))
            else -> Result.Failure(
                CLXError(
                    CLXErrorCode.UNEXPECTED_ERROR,
                    "Unexpected status: $status"
                )
            )
        }
    }
}

/** GET with common headers, timeout and retry policy. */
internal suspend fun HttpClient.getWithRetry(
    url: String,
    appKey: String,
    timeoutMillis: Long,
    retryMax: Int,
    tag: String,
    extraRetryPredicate: (HttpResponse) -> Boolean = { it.status == HttpStatusCode.TooManyRequests }
): HttpResponse {
    CloudXLogger.d(tag, "HTTP → GET $url")
    return this.get(url) {
        headers { append("Authorization", "Bearer $appKey") }
        requestTimeoutMillis(timeoutMillis)
        retry {
            maxRetries = retryMax
            retryOnExceptionOrServerErrors()
            retryIf { _, resp -> extraRetryPredicate(resp) }
            constantDelay(
                millis = CLOUDX_DEFAULT_RETRY_MS,
                randomizationMs = 1000L,
                respectRetryAfterHeader = true
            )
        }
    }
}

/** Convenience wrapper that combines httpCatching + postJsonWithRetry + mapToResult */
internal suspend inline fun <T> httpCatching(
    tag: String,
    crossinline onOk: suspend (body: String) -> Result<T, CLXError>,
    crossinline onNoContent: suspend (resp: HttpResponse, body: String) -> Result<T, CLXError>,
    crossinline block: suspend () -> HttpResponse
): Result<T, CLXError> = httpCatching {
    block().mapToResult(tag, onOk, onNoContent)
}