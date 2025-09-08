package io.cloudx.cd.staticrenderer

import android.content.Context
import io.cloudx.sdk.internal.FullscreenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

fun StaticFullscreenAd(
    context: Context,
    adm: String,
    listener: FullscreenAd.Listener?
): FullscreenAd<FullscreenAd.Listener> =
    StaticFullscreenAdImpl(
        context,
        adm,
        listener
    )

private class StaticFullscreenAdImpl(
    private val context: Context,
    private val adm: String,
    override var listener: FullscreenAd.Listener?
) : FullscreenAd<FullscreenAd.Listener> {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val staticWebView: StaticWebView by lazy {
        StaticWebView(context, ExternalLinkHandlerImpl(context))
    }

    override fun load() {
        staticWebView.clickthroughEvent
            .onEach { listener?.onClick() }
            .launchIn(scope)

        scope.launch {
            staticWebView.hasUnrecoverableError.first { it }
            onError()
        }

        scope.launch {
            if (staticWebView.loadHtml(adm)) listener?.onLoad() else listener?.onLoadError()
        }
    }

    override fun show() {
        scope.launch {
            showCalled = true

            listener?.run {
                onShow()
                onImpression()
            }

            try {
                StaticAdActivity.show(context, staticWebView)
            } finally {
                listener?.onComplete()
                listener?.onHide()
            }
        }
    }

    private var showCalled = false

    private fun onError() {
        if (showCalled) listener?.onShowError() else listener?.onLoadError()
    }

    override fun destroy() {
        scope.cancel()
        staticWebView.destroy()
    }
}