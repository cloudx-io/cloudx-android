package io.cloudx.sdk.internal.network.httpclient

import io.cloudx.sdk.internal.ApplicationContext

internal fun interface UserAgentProvider {

    operator fun invoke(): String
}

internal fun UserAgentProvider(): UserAgentProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    WebBrowserUserAgentProvider(
        ApplicationContext()
    )
}