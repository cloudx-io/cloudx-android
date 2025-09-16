package io.cloudx.sdk

import android.app.Activity
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.CXInterstitialAd
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.CXRewardedInterstitialAd
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState

/**
 * This is an entry point to CloudX SDK.
 */
object CloudX {

    /**
     * Initializes CloudX SDK; essential first step before loading and displaying any ads.
     */
    @JvmStatic
    fun initialize(
        initParams: CloudXInitializationParams,
        listener: CloudXInitializationListener?
    ) {
        CXSdk.initialize(initParams, listener)
    }

    /**
     * Creates a standard banner (320x50) ad placement instance which then can be added to a view hierarchy and load/render ads.
     */
    @JvmStatic
    fun createBanner(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateBanner)
        return CloudXAdView(activity, placementName, AdType.Banner.Standard).apply {
            this.listener = listener
        }
    }

    /**
     * Creates MREC banner (300x250) ad placement instance which then can be added to a view hierarchy and load/render ads.
     */
    @JvmStatic
    fun createMREC(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateMrec)
        return CloudXAdView(activity, placementName, AdType.Banner.MREC).apply {
            this.listener = listener
        }
    }

    /**
     * Creates [CloudXInterstitialAd] ad instance responsible for rendering non-rewarded fullscreen ads.
     */
    @JvmStatic
    fun createInterstitial(
        placementName: String,
        listener: CloudXInterstitialListener?
    ): CloudXInterstitialAd? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateInterstitial)
        return CXInterstitialAd(placementName, listener)
    }


    /**
     * Creates [CloudXRewardedInterstitialAd] ad instance responsible for rendering non-rewarded fullscreen ads.
     */
    @JvmStatic
    fun createRewardedInterstitial(
        placementName: String,
        listener: CloudXRewardedInterstitialListener?
    ): CloudXRewardedInterstitialAd? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateRewarded)
        return CXRewardedInterstitialAd(placementName, listener)
    }

    /**
     * Creates Native small ad placement instance which then can be added to a view hierarchy and load/render ads.
     */
    @JvmStatic
    fun createNativeAdSmall(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateNative)
        return CloudXAdView(activity, placementName, AdType.Native.Small).apply {
            this.listener = listener
        }
    }

    /**
     * Creates Native Medium ad placement instance which then can be added to a view hierarchy and load/render ads.
     */
    @JvmStatic
    fun createNativeAdMedium(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.CreateNative)
        return CloudXAdView(activity, placementName, AdType.Native.Medium).apply {
            this.listener = listener
        }
    }

    @JvmStatic
    fun setLoggingEnabled(isEnabled: Boolean) {
        CXLogger.isEnabled = isEnabled
    }

    @JvmStatic
    fun setMinLogLevel(minLogLevel: CloudXLogLevel) {
        CXLogger.minLogLevel = minLogLevel
    }

    /**
     * Set privacy data which is then will be used in ad loading process.
     */
    @JvmStatic
    fun setPrivacy(privacy: CloudXPrivacy) {
        PrivacyService().cloudXPrivacy.value = privacy
    }

    /**
     * Publisher is responsible for normalization and hashing of a user email
     */
    @JvmStatic
    fun setHashedUserId(hashedEmail: String) {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.SetHashedUserId)
        SdkKeyValueState.hashedUserId = hashedEmail
    }


    /**
     * Publisher can provide additional user key-value pairs.
     */
    @JvmStatic
    fun setUserKeyValue(key: String, value: String) {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.SetUserKeyValues)
        SdkKeyValueState.userKeyValues[key] = value
    }

    /**
     * Publisher can provide additional app key-value pairs.
     */
    @JvmStatic
    fun setAppKeyValue(key: String, value: String) {
        CXSdk.initializationService?.metricsTracker?.trackMethodCall(MetricsType.Method.SetAppKeyValues)
        SdkKeyValueState.appKeyValues[key] = value
    }

    @JvmStatic
    fun clearAllKeyValues() {
        SdkKeyValueState.clear()
    }

    @JvmStatic
    fun deinitialize() {
        CXSdk.deinitialize()
    }
}
