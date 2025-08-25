package io.cloudx.sdk.internal

import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.InterstitialListener
import io.cloudx.sdk.RewardedInterstitialListener

// TODO. Refactor. Ugly Naming is ugh. Functionality isn't better.

internal fun CloudXAdViewListener?.decorate(): CloudXAdViewListener =
    this ?: object : CloudXAdViewListener {
        override fun onAdLoaded(cloudXAd: CloudXAd) {}

        override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdDisplayed(cloudXAd: CloudXAd) {}

        override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdHidden(cloudXAd: CloudXAd) {}

        override fun onAdClicked(cloudXAd: CloudXAd) {}

        override fun onAdExpanded(placementName: String) {}

        override fun onAdCollapsed(placementName: String) {}
    }

internal fun InterstitialListener?.decorate(): InterstitialListener =
    this ?: object : InterstitialListener {
        override fun onAdLoaded(cloudXAd: CloudXAd) {}

        override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdDisplayed(cloudXAd: CloudXAd) {}

        override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdHidden(cloudXAd: CloudXAd) {}

        override fun onAdClicked(cloudXAd: CloudXAd) {}
    }

internal fun RewardedInterstitialListener?.decorate(): RewardedInterstitialListener =
    this ?: object : RewardedInterstitialListener {

        override fun onUserRewarded(cloudXAd: CloudXAd) {}

        override fun onAdLoaded(cloudXAd: CloudXAd) {}

        override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdDisplayed(cloudXAd: CloudXAd) {}

        override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {}

        override fun onAdHidden(cloudXAd: CloudXAd) {}

        override fun onAdClicked(cloudXAd: CloudXAd) {}
    }