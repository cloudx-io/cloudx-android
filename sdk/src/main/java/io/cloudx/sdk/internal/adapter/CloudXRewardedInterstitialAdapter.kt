package io.cloudx.sdk.internal.adapter

import android.os.Bundle
import io.cloudx.sdk.CloudXDestroyable
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

interface CloudXRewardedInterstitialAdapterFactory : CloudXAdapterMetaData {

    fun create(
        contextProvider: ContextProvider,
        placementName: String,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXRewardedInterstitialAdapterListener
    ): Result<CloudXRewardedInterstitialAdapter, String>
}

interface CloudXRewardedInterstitialAdapter : CloudXAdLoadOperationAvailability, CloudXDestroyable {

    fun load()
    fun show()
}

interface CloudXRewardedInterstitialAdapterListener : CloudXAdapterErrorListener {

    fun onLoad()
    fun onShow()
    fun onImpression()
    fun onEligibleForReward()
    fun onHide()
    fun onClick()
}