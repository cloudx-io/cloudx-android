package io.cloudx.adapter.cloudx

import android.content.Context
import io.cloudx.cd.staticrenderer.StaticFullscreenAd
import io.cloudx.cd.staticrenderer.FullscreenAd
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener

internal class StaticBidInterstitial(
    context: Context,
    adm: String,
    listener: CloudXInterstitialAdapterListener
) : CloudXInterstitialAdapter,
    CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val staticFullscreenAd = StaticFullscreenAd(
        context,
        adm,
        object : FullscreenAd.Listener {
            override fun onLoad() {
                listener.onLoad()
            }

            override fun onLoadError() {
                listener.onError()
            }

            override fun onShow() {
                listener.onShow()
            }

            override fun onShowError() {
                listener.onError()
            }

            override fun onImpression() {
                listener.onImpression()
            }

            override fun onComplete() {
                listener.onComplete()
            }

            override fun onClick() {
                listener.onClick()
            }

            override fun onHide() {
                listener.onHide()
            }
        }
    )

    override fun load() {
        staticFullscreenAd.load()
    }

    override fun show() {
        staticFullscreenAd.show()
    }

    override fun destroy() {
        staticFullscreenAd.destroy()
    }
}