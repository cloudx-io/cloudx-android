package io.cloudx.cd.staticrenderer

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader

open class BaseWebView(context: Context) : WebView(context.applicationContext) {
    // This kind of helps avoiding WebView memory leaks.
    override fun destroy() {
        (parent as? ViewGroup)?.removeView(this)
        removeAllViews()
        // TODO. IMPORTANT. Delay for OMID.
        super.destroy()
    }
}

fun BaseWebView.onClick(onClick: () -> Unit) {
    setOnTouchListener(createOnClickOnTouchCompatListener(onClick))
}

fun WebView.loadDataWithDefaultBaseUrl(data: String) {
    this.loadDataWithBaseURL(DEFAULT_BASE_URL, data, "text/html", "utf-8", null)
}

// https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
private const val DEFAULT_BASE_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}"

// Quick CSS fix for overriding default 8px margin/padding for body, removed zoom and overscrolling.
internal fun applyCSSRenderingFix(toHtml: String): String = CSSRenderingFixPrefix + toHtml

// TODO. Ideally, <meta> is supposed to be in <head>, but it seems to work anyways.
private const val CSSRenderingFixPrefix = """
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"> 
    <style> body { margin:0; padding:0; overflow:hidden; } </style>
"""