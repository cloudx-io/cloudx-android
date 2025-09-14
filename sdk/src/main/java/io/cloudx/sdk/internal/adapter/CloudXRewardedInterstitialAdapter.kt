package io.cloudx.sdk.internal.adapter

import android.os.Bundle
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

interface CloudXRewardedInterstitialAdapterFactory : CloudXAdapterMetaData {

    fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXRewardedInterstitialAdapterListener
    ): Result<CloudXRewardedInterstitialAdapter, String>
}

interface CloudXRewardedInterstitialAdapter : CloudXAdLoadOperationAvailability, Destroyable {

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