package io.cloudx.adapter.mintegral

import android.app.Activity
import com.mbridge.msdk.out.MBBidRewardVideoHandler
import com.mbridge.msdk.out.RewardVideoListener
import com.mbridge.msdk.out.MBridgeIds
import com.mbridge.msdk.out.RewardInfo
import io.cloudx.sdk.internal.adapter.CloudXAdLoadOperationAvailability
import io.cloudx.sdk.internal.adapter.AlwaysReadyToLoadAd
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitial
import io.cloudx.sdk.internal.adapter.CloudXRewardedInterstitialListener

internal class RewardedInterstitialAdapter(
    private val activity: Activity,
    private val placementId: String?,
    private val adUnitId: String,
    private val bidId: String?,
    private var listener: CloudXRewardedInterstitialListener?
) : CloudXRewardedInterstitial, CloudXAdLoadOperationAvailability by AlwaysReadyToLoadAd {

    private var adHandler: MBBidRewardVideoHandler? = null

    override fun load() {
        if (placementId.isNullOrBlank() || adUnitId.isBlank() || bidId.isNullOrBlank()) {
            val error = CloudXAdapterError(description = "some of the ids are null or blank")
            listener?.onError(error)
            return
        }

        val adHandler = MBBidRewardVideoHandler(activity, placementId, adUnitId)
        this.adHandler = adHandler

        adHandler.setRewardVideoListener(object : RewardVideoListener {

            override fun onLoadSuccess(p0: MBridgeIds?) {
            }

            override fun onVideoLoadSuccess(p0: MBridgeIds?) {
                listener?.onLoad()
            }

            override fun onVideoLoadFail(p0: MBridgeIds?, p1: String?) {
                val error = CloudXAdapterError(description = p1 ?: "")
                listener?.onError(error)
            }

            override fun onAdShow(p0: MBridgeIds?) {
                listener?.onShow()
                listener?.onImpression()
            }

            override fun onShowFail(p0: MBridgeIds?, p1: String?) {
                val error = CloudXAdapterError(description = p1 ?: "")
                listener?.onError(error)
            }

            override fun onVideoAdClicked(p0: MBridgeIds?) {
                listener?.onClick()
            }

            override fun onAdClose(p0: MBridgeIds?, p1: RewardInfo?) {
                if (p1?.isCompleteView == true) {
                    listener?.onEligibleForReward()
                }
                listener?.onHide()
            }

            override fun onVideoComplete(p0: MBridgeIds?) {
            }

            override fun onEndcardShow(p0: MBridgeIds?) {
            }
        })

        adHandler.loadFromBid(bidId)
    }

    override fun show() {
        val adHandler = this.adHandler
        if (adHandler == null) {
            listener?.onError(CloudXAdapterError(description = "can't show: ad is not loaded"))
            return
        }

        adHandler.showFromBid()
    }

    override fun destroy() {
        listener = null

        adHandler?.clearVideoCache()
        adHandler = null
    }
}