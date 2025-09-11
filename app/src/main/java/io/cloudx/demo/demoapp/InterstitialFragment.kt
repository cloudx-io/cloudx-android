package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXInterstitialListener

class InterstitialFragment : FullPageAdFragment() {

    override fun createAd(listener: CloudXAdListener) = CloudXInterstitialAd(
        placementName,
        object : CloudXInterstitialListener, CloudXAdListener by listener {}
    )

    override val adType: String = "Interstitial"
    override val logTag: String = "InterstitialFragment"
}