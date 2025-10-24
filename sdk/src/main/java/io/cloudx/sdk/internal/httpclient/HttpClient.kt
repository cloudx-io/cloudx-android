package io.cloudx.sdk.internal.httpclient

import io.cloudx.sdk.internal.CXLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

internal fun CloudXHttpClient(): HttpClient = LazySingleInstance

private val LazySingleInstance by lazy {
    HttpClient {
        install(UserAgent) {
            agent = UserAgentProvider()()
        }
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    CXLogger.d("CloudXHttpClient", message)
                }
            }
            level = if (CXLogger.isEnabled) LogLevel.ALL else LogLevel.NONE
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }
}