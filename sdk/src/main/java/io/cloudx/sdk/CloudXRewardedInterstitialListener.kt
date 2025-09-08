package io.cloudx.sdk

interface CloudXRewardedInterstitialListener : CloudXAdListener {

    /**
     * User was rewarded.
     * The [cloudXAd] object, will tell you which network it was.
     */
    fun onUserRewarded(cloudXAd: CloudXAd)
}