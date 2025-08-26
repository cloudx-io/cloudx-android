package io.cloudx.adapter.testbidnetwork

import android.app.Activity
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.ts.staticrenderer.ExternalLinkHandlerImpl
import io.cloudx.ts.staticrenderer.StaticWebView

internal class StaticBidBanner(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val adm: String,
    private val listener: CloudXAdViewAdapterListener
) : CloudXAdViewAdapter {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val staticWebView: StaticWebView by lazy {
        StaticWebView(activity, ExternalLinkHandlerImpl(activity))
    }

    override fun load() {
        staticWebView.clickthroughEvent
            .onEach { listener.onClick() }
            .launchIn(scope)

        scope.launch {
            staticWebView.hasUnrecoverableError.first { it }
            listener.onError()
        }

        scope.launch {
            adViewContainer.onAdd(staticWebView)

            if (staticWebView.loadHtml(adm)) {
                listener.onLoad()

                staticWebView.visibility = View.VISIBLE

                listener.onShow()
                listener.onImpression()
            } else {
                listener.onError()
            }
        }
    }

    override fun destroy() {
        scope.cancel()
        staticWebView.destroy()
        adViewContainer.onRemove(staticWebView)
    }
}