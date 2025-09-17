package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXInterstitialListener

class InterstitialFragment : FullPageAdFragment() {

    override fun createAd(listener: CloudXAdListener) = CloudX.createInterstitial(
        placementName
    ).apply {
        this.listener = object : CloudXInterstitialListener, CloudXAdListener by listener {}
    }

    override val adType: String = "Interstitial"
    override val logTag: String = "InterstitialFragment"
}