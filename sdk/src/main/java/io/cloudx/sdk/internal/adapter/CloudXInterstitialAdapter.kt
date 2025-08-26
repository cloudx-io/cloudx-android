package io.cloudx.sdk.internal.adapter

import android.app.Activity
import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.Result

interface CloudXInterstitialAdapterFactory : CloudXAdapterMetaData {

    fun create(
        activity: Activity,
        placementId: String,
        bidId: String,
        adm: String,
        params: Map<String, String>?,
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