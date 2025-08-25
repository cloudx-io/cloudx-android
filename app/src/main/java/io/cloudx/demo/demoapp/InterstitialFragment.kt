package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.InterstitialListener

class InterstitialFragment : FullPageAdFragment() {

    override fun createAd(listener: CloudXAdListener) = CloudX.createInterstitial(
        requireActivity(),
        placementName,
        object : InterstitialListener, CloudXAdListener by listener {}
    )

    override val adType: String = "Interstitial"
    override val logTag: String = "InterstitialFragment"
}