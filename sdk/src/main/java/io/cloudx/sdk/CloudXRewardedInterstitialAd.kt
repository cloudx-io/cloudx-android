package io.cloudx.sdk

import io.cloudx.sdk.CloudX.initialize
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.CXRewardedInterstitialAd

interface CloudXRewardedInterstitialAd : CloudXFullscreenAd

/**
 * Creates [CloudXRewardedInterstitialAd] ad instance responsible for rendering non-rewarded fullscreen ads.
 *
 * _General usage guideline:_
 * 1. Create [CloudXRewardedInterstitialAd] instance via invoking this function.
 * 2. If created successfully, consider attaching an optional [listener][CloudXRewardedInterstitialListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
 * 3. _Fullscreen ad implementations start precaching logic internally automatically in an optimised way, so you don't have to worry about any ad loading complexities.
 * We provide several APIs, use any appropriate ones for your use-cases:_
 *
 * - call [load()][CloudXFullscreenAd.load]; then wait for [onAdLoadSuccess()][CloudXAdListener.onAdLoaded] or [onAdLoadFailed()][CloudXAdListener.onAdLoadFailed] event;
 * - alternatively, check [isAdLoaded][CloudXFullscreenAd.isAdLoaded] property: if _true_ feel free to [show()][CloudXFullscreenAd.show] the ad;
 * - another option is to [setIsAdLoadedListener][CloudXFullscreenAd.setIsAdLoadedListener], which then always fires event upon internal loaded ad cache size changes.
 *
 * 4. call [show()][CloudXFullscreenAd.show] when you're ready to display an ad; then wait for [onAdShowSuccess()][CloudXAdListener.onAdDisplayed] or [onAdShowFailed()][CloudXAdListener.onAdDisplayFailed] event;
 * 5. Whenever parent Activity or Fragment is destroyed; or when ads are not required anymore - release ad instance resources via calling [destroy()][Destroyable.destroy]
 *
 * @param placementName identifier of CloudX placement setup on the dashboard.
 *
 * _Once SDK is [initialized][initialize] it knows which placement names are valid for ad creation_
 * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist
 * @sample io.cloudx.sdk.samples.createRewarded
 */
fun CloudXRewardedInterstitialAd(
    placementName: String,
    listener: CloudXRewardedInterstitialListener?
): CloudXRewardedInterstitialAd = CXRewardedInterstitialAd(placementName, listener)
