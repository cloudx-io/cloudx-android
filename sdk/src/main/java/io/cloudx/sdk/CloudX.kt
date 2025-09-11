package io.cloudx.sdk

import android.app.Activity
import io.cloudx.sdk.CloudX.createBanner
import io.cloudx.sdk.CloudX.createMREC
import io.cloudx.sdk.CloudX.createNativeAdMedium
import io.cloudx.sdk.CloudX.createNativeAdSmall
import io.cloudx.sdk.CloudX.createRewardedInterstitial
import io.cloudx.sdk.CloudX.initialize
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.initialization.InitializationService
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.cancellation.CancellationException

/**
 * This is an entry point to CloudX SDK.
 * Before creating any ad instances, [initialize] SDK first.
 */
object CloudX {
    private const val TAG = "CloudX"

    private val initializationService
        get() = (initState.value as? InitializationState.Initialized)?.initializationService
    private val _initState = MutableStateFlow<InitializationState>(InitializationState.Uninitialized)
    internal val initState: StateFlow<InitializationState> = _initState.asStateFlow()

    private val scope = MainScope()
    private var initJob: Job? = null
    private val mutex = Mutex()

    private val privacyService = PrivacyService()

    /**
     * Set privacy data which is then will be used in ad loading process.
     * @sample io.cloudx.sdk.samples.cloudXSetPrivacy
     */
    @JvmStatic
    fun setPrivacy(privacy: CloudXPrivacy) {
        privacyService.cloudXPrivacy.value = privacy
    }

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
     * - [createRewardedInterstitial]
     *
     * @param initParams initialization credentials and misc parameters
     * @param listener an optional listener to receive initialization status updates
     * @see isInitialized
     * @sample io.cloudx.sdk.samples.cloudXInitialize
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        initParams: InitializationParams,
        listener: CloudXInitializationListener? = null
    ) {
        if (!_initState.compareAndSet(
                expect = InitializationState.Uninitialized,
                update = InitializationState.Initializing
            )
        ) {
            val status = when (initState.value) {
                InitializationState.Uninitialized -> {
                    // Uninitialized/Failed due to a race; let caller try again
                    CloudXInitializationStatus(
                        initialized = false,
                        description = "Initialization not started"
                    )
                }

                InitializationState.Initializing -> {
                    CloudXInitializationStatus(
                        initialized = false,
                        description = "Initialization is already in progress"
                    )
                }

                is InitializationState.Initialized -> {
                    CloudXInitializationStatus(
                        initialized = true,
                        description = "Already initialized"
                    )
                }
            }

            ThreadUtils.runOnMainThread {
                listener?.onCloudXInitializationStatus(status)
            }
            return
        }

        initJob = scope.launch {
            val initStatus = try {
                // Initial creation of InitializationService.
                val initService = InitializationService(
                    configApi = ConfigApi(initParams.initEndpointUrl)
                )
                SdkKeyValueState.hashedUserId = initParams.hashedUserId
                initService.metricsTrackerNew?.trackMethodCall(MetricsType.Method.SdkInitMethod)

                // Initializing SDK...
                when (val result = initService.initialize(initParams.appKey)) {
                    is Result.Failure -> {
                        CloudXLogger.e(
                            TAG,
                            "SDK initialization failed: ${result.value.effectiveMessage}",
                            result.value.cause
                        )
                        _initState.value = InitializationState.Uninitialized
                        CloudXInitializationStatus(
                            initialized = false,
                            result.value.effectiveMessage,
                            result.value.code.code
                        )
                    }

                    is Result.Success -> {
                        CloudXLogger.i(TAG, "SDK initialization succeeded")
                        _state.value = InitializationState.Initialized(initService)
                        CloudXInitializationStatus(
                            initialized = true,
                            "CloudX SDK is initialized v${BuildConfig.SDK_VERSION_NAME}"
                        )
                    }
                }
            } catch (ce: CancellationException) {
                // Don’t swallow coroutine cancellation
                _initState.value = InitializationState.Uninitialized
                throw ce
            } catch (e: Exception) {
                CloudXLogger.e(TAG, "SDK initialization failed with exception", e)
                _initState.value = InitializationState.Uninitialized
                CloudXInitializationStatus(false, "CloudX SDK failed to initialize")
            }
            CloudXLogger.i(
                TAG,
                "Initialization complete, calling listener with status: ${initStatus.initialized}"
            )
            ThreadUtils.runOnMainThread {
                listener?.onCloudXInitializationStatus(initStatus)
            }
        }
    }

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
     * Creates [CloudXRewardedAd] ad instance responsible for rendering non-rewarded fullscreen ads.
     *
     * _General usage guideline:_
     * 1. Create [CloudXRewardedAd] instance via invoking this function.
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
    @JvmStatic
    fun createRewardedInterstitial(
        placementName: String,
        listener: CloudXRewardedInterstitialListener?
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
        initJob?.cancel()
        initializationService?.deinitialize()
        _initState.value = InitializationState.Uninitialized
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
