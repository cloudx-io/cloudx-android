package io.cloudx.adapter.mintegral

import android.app.Activity
import android.view.ViewGroup
import com.mbridge.msdk.out.Campaign
import com.mbridge.msdk.out.Frame
import com.mbridge.msdk.out.MBBidNativeHandler
import com.mbridge.msdk.out.NativeListener
import com.mbridge.msdk.out.NativeListener.NativeTrackingListener
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdError


internal class NativeAdAdapter(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val placementId: String?,
    private val adUnitId: String,
    private val bidId: String?,
    private val adType: AdType.Native,
    private var listener: CloudXAdViewAdapterListener?
) : CloudXAdViewAdapter {

    private var adHandler: MBBidNativeHandler? = null
    private var adView: ViewGroup? = null

    override fun load() {
        if (placementId.isNullOrBlank() || adUnitId.isBlank() || bidId.isNullOrBlank()) {
            val error = CloudXAdError(description = "some of the ids are null or blank")
            listener?.onError(error)
            return
        }

        val adHandler = MBBidNativeHandler(
            MBBidNativeHandler.getNativeProperties(placementId, adUnitId),
            activity
        )
        this.adHandler = adHandler

        adHandler.setAdListener(createAdListener())
        adHandler.trackingListener = createTrackingListener()

        adHandler.bidLoad(bidId)
    }

    private fun createAdListener() = object :  NativeListener.NativeAdListener {

        override fun onAdFramesLoaded(p0: MutableList<Frame>?) {

        }

        override fun onAdLoadError(p0: String?) {
            val error = CloudXAdError(description = p0.toString())
            listener?.onError(error)
        }

        override fun onAdLoaded(p0: MutableList<Campaign>?, p1: Int) {
            val campaign = p0?.firstOrNull()
            if (campaign == null) {
                val error = CloudXAdError(description = "no campaigns")
                listener?.onError(error)
                return
            }

            val adHandler = this@NativeAdAdapter.adHandler
            if (adHandler == null) {
                val error = CloudXAdError(description = "Ad handler is unavailable")
                listener?.onError(error)
                return
            }

            val adView = activity.createNativeAdView(adHandler, adType, campaign, ::onClick)
            this@NativeAdAdapter.adView = adView

            adViewContainer.onAdd(adView)

            listener?.onLoad()
        }

        override fun onLoggingImpression(p0: Int) {
            listener?.onShow()
            listener?.onImpression()
        }

        override fun onAdClick(p0: Campaign?) {
            onClick()
        }
    }

    private fun createTrackingListener() = object: NativeTrackingListener {
        override fun onFinishRedirection(p0: Campaign?, p1: String?) {
        }

        override fun onRedirectionFailed(p0: Campaign?, p1: String?) {
        }

        override fun onStartRedirection(p0: Campaign?, p1: String?) {
        }

        override fun onDismissLoading(p0: Campaign?) {
        }

        override fun onDownloadFinish(p0: Campaign?) {
        }

        override fun onDownloadProgress(p0: Int) {
        }

        override fun onDownloadStart(p0: Campaign?) {
        }

        override fun onShowLoading(p0: Campaign?) {
        }

        override fun onInterceptDefaultLoadingDialog(): Boolean = false

    }

    override fun destroy() {
        listener = null

        adHandler?.let {
            it.setAdListener(null)
            it.trackingListener = null
            it.clearVideoCache()
            it.bidRelease()
        }
        adHandler = null

        adView?.let {
            adViewContainer.onRemove(it)
        }
        adView = null
    }

    private fun onClick() {
        listener?.onClick()
    }
}