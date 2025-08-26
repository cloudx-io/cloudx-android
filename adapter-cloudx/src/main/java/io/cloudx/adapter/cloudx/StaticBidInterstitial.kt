package io.cloudx.adapter.cloudx

import android.app.Activity
import io.cloudx.sdk.internal.FullscreenAd
import io.cloudx.sdk.internal.adapter.AdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapter
import io.cloudx.sdk.internal.adapter.CloudXInterstitialAdapterListener
import io.cloudx.cd.staticrenderer.StaticFullscreenAd

internal class StaticBidInterstitial(
    activity: Activity,
    adm: String,
    listener: CloudXInterstitialAdapterListener
) : CloudXInterstitialAdapter,
    AdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private val staticFullscreenAd = StaticFullscreenAd(
        activity,
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