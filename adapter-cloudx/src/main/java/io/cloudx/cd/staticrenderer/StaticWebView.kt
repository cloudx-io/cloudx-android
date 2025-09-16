package io.cloudx.cd.staticrenderer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.*
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import io.cloudx.sdk.internal.CXLogger

// TODO. Ugly. Duplication of VastWebView.
//  Review settings during webview build/init.
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class StaticWebView(
    context: Context,
    externalLinkHandler: ExternalLinkHandler
) : BaseWebView(context) {

    init {
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false

        with(settings) {
            setSupportZoom(false)
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        setBackgroundColor(Color.TRANSPARENT)

        visibility = GONE
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private val webViewClientImpl = WebViewClientImpl(scope, externalLinkHandler).also {
        webViewClient = it
    }

    val hasUnrecoverableError: StateFlow<Boolean> = webViewClientImpl.hasUnrecoverableError
    val clickthroughEvent: SharedFlow<Unit> = webViewClientImpl.clickthroughEvent

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    // TODO. Refactor.
    // Currently it's single-time use, so no worries regarding isLoaded flag volatility.
    suspend fun loadHtml(html: String): Boolean = coroutineScope {
        withContext(Dispatchers.Main) {
            try {
                loadDataWithDefaultBaseUrl(applyCSSRenderingFix(html))
            } catch (e: Exception) {
                CXLogger.e(message = e.toString())
            }

            // Aahahah.
            val isLoaded = webViewClientImpl.isLoaded
                .combine(webViewClientImpl.hasUnrecoverableError) { isLoaded, hasUnrecoverableError ->
                    isLoaded to hasUnrecoverableError
                }.first {
                    val (isLoaded, hasUnrecoverableError) = it
                    isLoaded || hasUnrecoverableError
                }.first

            return@withContext isLoaded
        }
    }
}

// TODO. Refactor. Also logging for errors and stuff.
private class WebViewClientImpl(
    private val scope: CoroutineScope,
    private val externalLinkHandler: ExternalLinkHandler
) : WebViewClientCompat() {

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    private val _hasUnrecoverableError = MutableStateFlow(false)
    val hasUnrecoverableError: StateFlow<Boolean> = _hasUnrecoverableError

    // TODO. Why not just a simple listener? Auto cancellation of late events is cool though.
    private val _clickthroughEvent = MutableSharedFlow<Unit>()
    val clickthroughEvent: SharedFlow<Unit> = _clickthroughEvent

    // Deprecated, but at least works everywhere.
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (externalLinkHandler(url ?: "")) {
            scope.launch { _clickthroughEvent.emit(Unit) }
        }
        // Stop loading the url in the webview.
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view?.progress == 100) {
            _isLoaded.value = true
        }
    }

    // Looking for unrecoverable errors only.
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        _hasUnrecoverableError.value = true
        CXLogger.e(TAG, "onReceivedError $description")
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?
    ): Boolean {
        // TODO. Logging.
        // https://developer.android.com/guide/webapps/managing-webview#termination-handle
        // Basically, then webview will be destroyed externally after this, which, ideally, isn't known here.
        // But who cares, plus deadlines.
        _hasUnrecoverableError.value = true
        CXLogger.e(TAG, "onRenderProcessGone")
        return true
    }

    companion object {
        private const val TAG = "WebViewClientImpl"
    }
}