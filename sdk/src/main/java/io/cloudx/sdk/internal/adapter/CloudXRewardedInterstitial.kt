package io.cloudx.sdk.internal.adapter

import android.app.Activity
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result

interface CloudXRewardedInterstitialAdapterFactory : CloudXAdapterMetaData {

    fun create(
        activity: Activity,
        adId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
        listener: CloudXRewardedInterstitialListener
    ): Result<CloudXRewardedInterstitial, String>
}

interface CloudXRewardedInterstitial : CloudXAdLoadOperationAvailability, Destroyable {

    fun load()
    fun show()
}

interface CloudXRewardedInterstitialListener : CloudXAdapterErrorListener {

    fun onLoad()
    fun onShow()
    fun onImpression()
    fun onEligibleForReward()
    fun onHide()
    fun onClick()
}