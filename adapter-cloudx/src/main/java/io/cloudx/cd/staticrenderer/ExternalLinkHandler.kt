package io.cloudx.cd.staticrenderer

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

fun interface ExternalLinkHandler {
    operator fun invoke(uri: String): Boolean
}

class ExternalLinkHandlerImpl(private val context: Context): ExternalLinkHandler {
    override fun invoke(uri: String): Boolean {
        return context.tryStartCustomTabs(uri)
    }
}

// https://developer.chrome.com/multidevice/android/customtabs
private fun Context.tryStartCustomTabs(uri: String): Boolean {
    return try {
        val customTabsIntent: CustomTabsIntent = CustomTabsIntent.Builder()
            .setUrlBarHidingEnabled(true)
            .build()

        customTabsIntent.launchUrl(this, Uri.parse(uri))
        true
    } catch (e: Exception) {
        false
    }
}