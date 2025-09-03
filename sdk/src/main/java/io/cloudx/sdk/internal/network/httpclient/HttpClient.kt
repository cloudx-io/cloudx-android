package io.cloudx.sdk.internal.network.httpclient

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

internal fun CloudXHttpClient(): HttpClient = LazySingleInstance

private val LazySingleInstance by lazy {
    HttpClient {
        install(UserAgent) {
            agent = UserAgentProvider()()
        }
        install(HttpTimeout)
        install(HttpRequestRetry)
    }
}