package io.cloudx.adapter.googleadmanager

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.BaseAdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterError

internal class BannerAdapter(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val adUnitId: String,
    private val adViewSize: AdViewSize,
    private val listener: CloudXAdViewAdapterListener
) : CloudXAdViewAdapter {

    private var ad: BaseAdView? = null

    override fun load() {
        val ad = AdManagerAdView(activity)
        this.ad = ad

        with(ad) {
            setAdSizes(
                if (adViewSize == AdViewSize.Standard) AdSize.BANNER else AdSize.MEDIUM_RECTANGLE
            )

            adUnitId = this@BannerAdapter.adUnitId
            adListener = createListener(listener)

            adViewContainer.onAdd(this)

            loadAd(AdManagerAdRequest.Builder().build())
        }
    }

    private fun createListener(listener: CloudXAdViewAdapterListener?) = object : AdListener() {

        override fun onAdLoaded() {
            super.onAdLoaded()
            listener?.onLoad()
        }

        override fun onAdFailedToLoad(p0: LoadAdError) {
            super.onAdFailedToLoad(p0)
            listener?.onError(CloudXAdapterError(description = p0.toString()))
        }

        override fun onAdImpression() {
            super.onAdImpression()
            listener?.onShow()
            listener?.onImpression()
        }

        override fun onAdClicked() {
            super.onAdClicked()
            listener?.onClick()
        }
    }

    override fun destroy() {
        ad?.let {
            it.adListener = createListener(null)
            it.destroy()

            adViewContainer.onRemove(it)
        }

        ad = null
    }
}