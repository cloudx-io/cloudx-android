package io.cloudx.sdk

import android.app.Activity
import android.content.Context
import io.cloudx.sdk.CloudX.initialize
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adfactory.AdFactory
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.initialization.InitializationService
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * This is an entry point to CloudX SDK.
 * Before creating any ad instances, [initialize] SDK first.
 */
object CloudX {

    // TODO. Ensure all public apis are redirected to UI thread.
    private val scope = MainScope()

    private val privacyService = PrivacyService()

    /**
     * Set privacy data which is then will be used in ad loading process.
     * @sample io.cloudx.sdk.samples.cloudXSetPrivacy
     */
    @JvmStatic
    fun setPrivacy(privacy: CloudXPrivacy) {
        privacyService.cloudXPrivacy.value = privacy
    }

    private var initializationService: InitializationService? = null

    /**
     * Current initialization status of CloudX SDK.
     * @see initialize
     */
    @JvmStatic
    val isInitialized: Boolean
        get() = initializationService?.initialized ?: false

    /**
     * Initializes CloudX SDK; essential first step before loading and displaying any ads.
     *
     * If the SDK is already initialized, the provided listener will receive [CloudXInitializationStatus.initialized] - true.
     *
     * After a successful initialization, you can:
     * - [createBanner]
     * - [createMREC]
     * - [createNativeAdSmall]
     * - [createNativeAdMedium]
     * - [createInterstitial]
     * - [createRewardedInterstitial]
     *
     * @param initializationParams initialization credentials and misc parameters
     * @param listener an optional listener to receive initialization status updates
     * @see isInitialized
     * @sample io.cloudx.sdk.samples.cloudXInitialize
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        initializationParams: InitializationParams,
        listener: CloudXInitializationListener? = null
    ) {
        // Initialization is already in progress.
        if (initJob?.isActive == true) {
            listener?.onCloudXInitializationStatus(
                CloudXInitializationStatus(
                    initialized = false, "Initialization is already in progress"
                )
            )
            return
        }

        initJob = scope.launch {
            SdkKeyValueState.hashedUserId = initializationParams.hashedUserId

            // Already initialized.
            if (isInitialized) {
                listener?.onCloudXInitializationStatus(
                    CloudXInitializationStatus(initialized = true, "Already initialized")
                )
                return@launch
            }

            // Initial creation of InitializationService.
            val initializationService = InitializationService(
                context = context,
                configApi = ConfigApi(initializationParams.initEndpointUrl)
            )
            this@CloudX.initializationService = initializationService
            initializationService.metricsTrackerNew?.trackMethodCall(MetricsType.Method.SdkInitMethod)

            // Initializing SDK...
            val initStatus =
                when (val result = initializationService.initialize(initializationParams.appKey)) {
                    is Result.Failure -> CloudXInitializationStatus(
                        initialized = false, result.value.effectiveMessage, result.value.code.code
                    )

                    is Result.Success -> CloudXInitializationStatus(
                        initialized = true,
                        "CloudX SDK is initialized v${BuildConfig.SDK_VERSION_NAME}"
                    )
                }

            listener?.onCloudXInitializationStatus(initStatus)
        }
    }

    private var initJob: Job? = null

    /**
     * Creates a standard banner (320x50) ad placement instance which then can be added to a view hierarchy and load/render ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXAdView] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][CloudXAdViewListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
     * 3. Attach [CloudXAdView] to the view hierarchy; ads start loading and displaying automatically, depending on placement's ad refresh rate (comes in init config)
     * 4. Whenever parent Activity or Fragment is destroyed; or when ads are not required anymore - release ad instance resources via calling [destroy()][io.cloudx.sdk.Destroyable.destroy]
     * @param activity activity instance to which [CloudXAdView] instance is going to be attached to.
     * @param placementName identifier of CloudX placement setup on the dashboard.
     *
     * _Once SDK is [initialized][initialize] it knows which placement names are valid for ad creation_
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [CloudXAdView] instance.
     * @sample io.cloudx.sdk.samples.cloudXCreateAdView
     */
    @JvmStatic
    // @JvmOverloads. Uncomment when optional parameters are added.
    fun createBanner(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateBanner)

        val bannerParams = AdFactory.CreateBannerParams(
            AdType.Banner.Standard,
            activity,
            placementName,
            listener
        )

        return initializationService?.adFactory?.createBanner(bannerParams)
    }

    /**
     * Creates MREC banner (300x250) ad placement instance which then can be added to a view hierarchy and load/render ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXAdView] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][CloudXAdViewListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
     * 3. Attach [CloudXAdView] to the view hierarchy; ads start loading and displaying automatically, depending on placement's ad refresh rate (comes in init config)
     * 4. Whenever parent Activity or Fragment is destroyed; or when ads are not required anymore - release ad instance resources via calling [destroy()][io.cloudx.sdk.Destroyable.destroy]
     * @param activity activity instance to which [CloudXAdView] instance is going to be attached to.
     * @param placementName identifier of CloudX placement setup on the dashboard.
     *
     * _Once SDK is [initialized][initialize] it knows which placement names are valid for ad creation_
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [CloudXAdView] instance.
     * @sample io.cloudx.sdk.samples.cloudXCreateAdView
     */
    @JvmStatic
    // @JvmOverloads. Uncomment when optional parameters are added.
    fun createMREC(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateMrec)
        return initializationService?.adFactory?.createBanner(
            AdFactory.CreateBannerParams(
                AdType.Banner.MREC,
                activity,
                placementName,
                listener
            )
        )
    }

    /**
     * Creates [CloudXInterstitialAd] ad instance responsible for rendering non-rewarded fullscreen ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXInterstitialAd] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][CloudXInterstitialListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
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
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [Interstitial] ad instance.
     * @sample io.cloudx.sdk.samples.createInterstitial
     */
    @JvmStatic
    fun createInterstitial(
        placementName: String,
        listener: CloudXInterstitialListener?
    ): CloudXInterstitialAd? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateInterstitial)
        return initializationService?.adFactory?.createInterstitial(
            AdFactory.CreateAdParams(placementName, listener)
        )
    }

    /**
     * Creates [CloudXRewardedAd] ad instance responsible for rendering non-rewarded fullscreen ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXRewardedAd] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][RewardedInterstitialListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
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
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [RewardedInterstitial] ad instance.
     * @sample io.cloudx.sdk.samples.createRewarded
     */
    @JvmStatic
    fun createRewardedInterstitial(
        placementName: String,
        listener: RewardedInterstitialListener?
    ): CloudXRewardedAd? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateRewarded)
        return initializationService?.adFactory?.createRewarded(
            AdFactory.CreateAdParams(placementName, listener)
        )
    }

    /**
     * Creates Native small ad placement instance which then can be added to a view hierarchy and load/render ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXAdView] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][CloudXAdViewListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
     * 3. Attach [CloudXAdView] to the view hierarchy; ads start loading and displaying automatically, depending on placement's ad refresh rate (comes in init config)
     * 4. Whenever parent Activity or Fragment is destroyed; or when ads are not required anymore - release ad instance resources via calling [destroy()][io.cloudx.sdk.Destroyable.destroy]
     * @param activity activity instance to which [CloudXAdView] instance is going to be attached to.
     * @param placementName identifier of CloudX placement setup on the dashboard.
     *
     * _Once SDK is [initialized][initialize] it knows which placement names are valid for ad creation_
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [CloudXAdView] instance.
     * @sample io.cloudx.sdk.samples.cloudXCreateAdView
     */
    @JvmStatic
    // @JvmOverloads. Uncomment when optional parameters are added.
    fun createNativeAdSmall(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateNative)
        return initializationService?.adFactory?.createBanner(
            AdFactory.CreateBannerParams(
                AdType.Native.Small,
                activity,
                placementName,
                listener
            )
        )
    }

    /**
     * Creates Native Medium ad placement instance which then can be added to a view hierarchy and load/render ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXAdView] instance via invoking this function.
     * 2. If created successfully, consider attaching an optional [listener][CloudXAdViewListener] which is then can be used for tracking ad events (impression, click, hidden, etc)
     * 3. Attach [CloudXAdView] to the view hierarchy; ads start loading and displaying automatically, depending on placement's ad refresh rate (comes in init config)
     * 4. Whenever parent Activity or Fragment is destroyed; or when ads are not required anymore - release ad instance resources via calling [destroy()][io.cloudx.sdk.Destroyable.destroy]
     * @param activity activity instance to which [CloudXAdView] instance is going to be attached to.
     * @param placementName identifier of CloudX placement setup on the dashboard.
     *
     * _Once SDK is [initialized][initialize] it knows which placement names are valid for ad creation_
     * @return _null_ - if SDK didn't [initialize] successfully/yet or [placementName] doesn't exist, else [CloudXAdView] instance.
     * @sample io.cloudx.sdk.samples.cloudXCreateAdView
     */
    @JvmStatic
    // @JvmOverloads. Uncomment when optional parameters are added.
    fun createNativeAdMedium(
        activity: Activity,
        placementName: String,
        listener: CloudXAdViewListener?
    ): CloudXAdView? {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.CreateNative)
        return initializationService?.adFactory?.createBanner(
            AdFactory.CreateBannerParams(
                AdType.Native.Medium,
                activity,
                placementName,
                listener
            )
        )
    }


    /**
     * Publisher is responsible for normalization and hashing of a user email
     * .
     *
     */
    @JvmStatic
    fun setHashedUserId(hashedEmail: String) {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.SetHashedUserId)
        SdkKeyValueState.hashedUserId = hashedEmail
    }


    /**
     * Publisher can provide additional user key-value pairs.
     */
    @JvmStatic
    fun setUserKeyValue(key: String, value: String) {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.SetUserKeyValues)
        SdkKeyValueState.userKeyValues[key] = value
    }

    /**
     * Publisher can provide additional app key-value pairs.
     */
    @JvmStatic
    fun setAppKeyValue(key: String, value: String) {
        initializationService?.metricsTrackerNew?.trackMethodCall(MetricsType.Method.SetAppKeyValues)
        SdkKeyValueState.appKeyValues[key] = value
    }

    @JvmStatic
    fun clearAllKeyValues() {
        SdkKeyValueState.clear()
    }

    @JvmStatic
    fun deinitialize() {
        initializationService?.deinitialize()
        initializationService = null
    }

    /**
     * Initialization params
     *
     * @property appKey - Identifier of the publisher app registered with CloudX.
     * @property initEndpointUrl - endpoint to fetch an initial SDK configuration from
     */
    class InitializationParams(
        val appKey: String,
        val initEndpointUrl: String,
        val hashedUserId: String? = null
    )
}
