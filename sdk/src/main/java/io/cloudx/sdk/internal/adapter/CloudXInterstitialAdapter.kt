package io.cloudx.sdk.internal.adapter

import android.os.Bundle
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.context.ContextProvider
import io.cloudx.sdk.internal.util.Result

interface CloudXInterstitialAdapterFactory : CloudXAdapterMetaData {

    fun create(
        contextProvider: ContextProvider,
        placementId: String,
        bidId: String,
        adm: String,
        serverExtras: Bundle,
        listener: CloudXInterstitialAdapterListener
    ): Result<CloudXInterstitialAdapter, String>
}

interface CloudXInterstitialAdapter : CloudXAdLoadOperationAvailability, Destroyable {

    fun load()
    fun show()
}

interface CloudXInterstitialAdapterListener : CloudXAdapterErrorListener {

    fun onLoad()
    fun onShow()
    fun onImpression()
    fun onSkip()
    fun onComplete()
    fun onHide()
    fun onClick()
}