package io.cloudx.cd.staticrenderer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

// TODO. Ugly. Duplication of VastWebView.
//  Review settings during webview build/init.
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class StaticWebView(
    context: Context,
    externalLinkHandler: ExternalLinkHandler
) : BaseWebView(context) {

    private val logger = CXLogger.forComponent("StaticWebView")

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

    private val scope = ThreadUtils.createMainScope("StaticWebView")

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e.toString())
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

    private val logger = CXLogger.forComponent("WebViewClientImpl")

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
        logger.e("onReceivedError $description")
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
        logger.e("onRenderProcessGone")
        return true
    }
}