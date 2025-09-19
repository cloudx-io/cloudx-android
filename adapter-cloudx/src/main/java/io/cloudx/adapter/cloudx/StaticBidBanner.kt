package io.cloudx.adapter.cloudx

import android.view.View
import io.cloudx.cd.staticrenderer.ExternalLinkHandlerImpl
import io.cloudx.cd.staticrenderer.StaticWebView
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.ThreadUtils
import io.cloudx.sdk.toCloudXError
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class StaticBidBanner(
    private val contextProvider: ContextProvider,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val adm: String,
    private val listener: CloudXAdViewAdapterListener
) : CloudXAdViewAdapter {

    private val scope = ThreadUtils.createMainScope("StaticBidBanner")

    private val staticWebView: StaticWebView by lazy {
        StaticWebView(
            context = contextProvider.getContext(),
            externalLinkHandler = ExternalLinkHandlerImpl(contextProvider.getContext())
        )
    }

    override fun load() {
        staticWebView.clickthroughEvent
            .onEach { listener.onClick() }
            .launchIn(scope)

        scope.launch {
            staticWebView.hasUnrecoverableError.first { it }
            listener.onError(CloudXErrorCode.UNEXPECTED_ERROR.toCloudXError())
        }

        scope.launch {
            adViewContainer.onAdd(staticWebView)

            if (staticWebView.loadHtml(adm)) {
                listener.onLoad()

                staticWebView.visibility = View.VISIBLE

                listener.onShow()
                listener.onImpression()
            } else {
                listener.onError(CloudXErrorCode.UNEXPECTED_ERROR.toCloudXError())
            }
        }
    }

    override fun destroy() {
        scope.cancel()
        staticWebView.destroy()
        adViewContainer.onRemove(staticWebView)
    }
}