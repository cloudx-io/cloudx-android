package io.cloudx.sdk.internal.adapter

import io.cloudx.sdk.Destroyable

interface CloudXInterstitialAdapter : AdLoadOperationAvailability, Destroyable {

    fun load()
    fun show()
}

interface CloudXInterstitialAdapterListener : AdErrorListener {

    fun onLoad()
    fun onShow()
    fun onImpression()
    fun onSkip()
    fun onComplete()
    fun onHide()
    fun onClick()
}