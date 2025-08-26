package io.cloudx.adapter.mintegral

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.mbridge.msdk.out.BannerAdListener
import com.mbridge.msdk.out.BannerSize
import com.mbridge.msdk.out.BannerSize.DEV_SET_TYPE
import com.mbridge.msdk.out.BannerSize.MEDIUM_TYPE
import com.mbridge.msdk.out.BannerSize.STANDARD_TYPE
import com.mbridge.msdk.out.MBBannerView
import com.mbridge.msdk.out.MBridgeIds
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class BannerAdapter(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val placementId: String?,
    private val adUnitId: String,
    private val bidId: String?,
    private val adViewSize: AdViewSize,
    private var listener: CloudXAdViewAdapterListener?
) : CloudXAdViewAdapter {

    private var banner: MBBannerView? = null

    override fun load() {
        if (placementId.isNullOrBlank() || adUnitId.isBlank() || bidId.isNullOrBlank()) {
            val error = CloudXAdError(description = "some of the ids are null or blank")
            listener?.onError(error)
            return
        }

        val banner = MBBannerView(activity)
        this.banner = banner

        val mtgBannerType = when (adViewSize) {
            AdViewSize.Standard -> STANDARD_TYPE
            AdViewSize.MREC -> MEDIUM_TYPE
            else -> DEV_SET_TYPE
        }

        with(banner) {
            init(
                BannerSize(mtgBannerType, adViewSize.w, adViewSize.h),
                placementId,
                adUnitId
            )

            // Do not auto refresh.
            setRefreshTime(0)
            setAllowShowCloseBtn(false)

            setBannerAdListener(createListener())

            adViewContainer.onAdd(banner)
            attachLifecycleEventHandler()

            loadFromBid(bidId)
        }
    }

    private fun createListener() = object : BannerAdListener {

        override fun onLoadSuccessed(p0: MBridgeIds?) {
            listener?.onLoad()
        }

        override fun onLoadFailed(p0: MBridgeIds?, p1: String?) {
            val error = CloudXAdError(description = p1 ?: "")
            listener?.onError(error)
        }

        override fun onLogImpression(p0: MBridgeIds?) {
            listener?.onShow()
            listener?.onImpression()
        }

        override fun onClick(p0: MBridgeIds?) {
            listener?.onClick()
        }

        override fun onLeaveApp(p0: MBridgeIds?) {

        }

        override fun showFullScreen(p0: MBridgeIds?) {

        }

        override fun closeFullScreen(p0: MBridgeIds?) {

        }

        override fun onCloseBanner(p0: MBridgeIds?) {

        }
    }

    private var lifecycleEventJob: Job? = null

    private fun MBBannerView.attachLifecycleEventHandler() {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return

        val bannerView = this

        lifecycleEventJob?.cancel()
        lifecycleEventJob = lifecycleOwner.lifecycle.currentStateFlow.onEach {
            if (it.isAtLeast(Lifecycle.State.RESUMED)) {
                bannerView.onResume()
            } else {
                bannerView.onPause()
            }
        }.launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun destroy() {
        lifecycleEventJob?.cancel()
        lifecycleEventJob = null

        listener = null

        banner?.let {
            it.release()
            adViewContainer.onRemove(it)
        }
        banner = null
    }
}