package io.cloudx.sdk.internal.httpclient

import android.content.Context
import android.webkit.WebSettings
import io.cloudx.sdk.internal.ApplicationContext

private val LazySingleInstance by lazy {
    UserAgentProvider(ApplicationContext())
}

internal fun UserAgentProvider(): UserAgentProvider = LazySingleInstance

internal class UserAgentProvider(context: Context = ApplicationContext()) {

    private val ua: String by lazy {
        // On some devices this crashes thus we envelope it into try catch
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            // Let's use at least something.
            "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
        }
    }

    operator fun invoke() = ua
}