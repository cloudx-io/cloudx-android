package io.cloudx.sdk.internal.initialization

import android.content.Context
import com.xor.XorEncryption
import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.crash.CrashReportingService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.tracker.ClickCounterTracker
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsType
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.normalizeAndHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Initialization service impl - initializes CloudX SDK; ignores all the following init calls after successful initialization.
 */
internal class InitializationServiceImpl(
    private val context: Context,
    private val configApi: ConfigApi,
    private val provideConfigRequest: ConfigRequestProvider,
    private val adapterResolver: AdapterFactoryResolver,
    private val privacyService: PrivacyService,
    private val _metricsTracker: MetricsTracker,
    private val eventTracker: EventTracker,
    private val winLossTracker: WinLossTracker,
    private val provideDeviceInfo: DeviceInfoProvider,
    private val geoApi: GeoApi,
    private val crashReportingService: CrashReportingService,
    private val appInfoProvider: AppInfoProvider
) : InitializationService {

    private val TAG = "InitializationService"

    private var config: Config? = null
    private var appKey: String = ""
    private var basePayload: String = ""
    private var factories: BidAdNetworkFactories? = null

    override val metricsTracker: MetricsTracker
        get() = _metricsTracker

    override var adFactory: AdFactory? = null
        private set


    override suspend fun initialize(appKey: String): Result<Config, CloudXError> {
        CXLogger.i(TAG, "Starting SDK initialization with appKey: $appKey")
        this.appKey = appKey

        crashReportingService.registerSdkCrashHandler()

        val config = this.config
        if (config != null) {
            CXLogger.i(TAG, "SDK already initialized, returning cached config")
            return Result.Success(config)
        }

        val configApiResult: Result<Config, CloudXError>
        val configApiRequestMillis = measureTimeMillis {
            configApiResult = configApi.invoke(appKey, provideConfigRequest())
        }

        if (configApiResult is Result.Failure) {
            if (configApiResult.value.code == CloudXErrorCode.SDK_DISABLED) {
                CXLogger.w(
                    TAG,
                    "SDK disabled by server via traffic control (0%). No ads this session."
                )
            }
            return configApiResult
        }

        if (configApiResult is Result.Success) {
            val cfg = configApiResult.value
            this.config = cfg
            crashReportingService.setConfig(cfg)

            eventTracker.setEndpoint(cfg.trackingEndpointUrl)
            eventTracker.trySendingPendingTrackingEvents()

            winLossTracker.setAppKey(appKey)
            winLossTracker.setEndpoint(cfg.winLossNotificationUrl)
            winLossTracker.setPayloadMapping(cfg.winLossNotificationPayloadConfig)
            winLossTracker.trySendingPendingWinLossEvents()

            ResolvedEndpoints.resolveFrom(cfg)
            SdkKeyValueState.setKeyValuePaths(cfg.keyValuePaths)

            metricsTracker.start(cfg)

            val geoDataResult: Result<Map<String, String>, CloudXError>
            val geoRequestMillis = measureTimeMillis {
                geoDataResult = geoApi.fetchGeoHeaders(ResolvedEndpoints.geoEndpoint)
            }
            if (geoDataResult is Result.Success) {
                val headersMap = geoDataResult.value

                val geoInfo: Map<String, String> = cfg.geoHeaders?.mapNotNull { header ->
                    val sourceHeader = header.source
                    val targetField = header.target
                    val value = headersMap[sourceHeader]

                    value?.let {
                        targetField to it
                    }
                }?.toMap() ?: emptyMap()

                // TODO: Hardcoded for now, should be configurable later via config CX-919.
                val userGeoIp = headersMap["x-amzn-remapped-x-forwarded-for"]
                val hashedGeoIp = userGeoIp?.let { normalizeAndHash(userGeoIp) } ?: ""
                CXLogger.i(TAG, "User Geo IP: $userGeoIp")
                CXLogger.i(TAG, "Hashed Geo IP: $hashedGeoIp")
                TrackingFieldResolver.setHashedGeoIp(hashedGeoIp)

                CXLogger.i(TAG, "geo data: $geoInfo")
                GeoInfoHolder.setGeoInfo(geoInfo, headersMap)

                val removePii = privacyService.shouldClearPersonalData()
                CXLogger.i(TAG, "PII remove: $removePii")

                sendInitSDKEvent(cfg, appKey)

                val pendingCrash = crashReportingService.getPendingCrashIfAny()
                pendingCrash?.let {
                    crashReportingService.sendErrorEvent(it)
                }
            }

            val factories = resolveAdapters(cfg)

            val appKeyOverride = cfg.appKeyOverride ?: appKey
            initAdFactory(appKeyOverride, cfg, factories)
            initializeAdapterNetworks(cfg, context)

            metricsTracker.trackNetworkCall(MetricsType.Network.GeoApi, geoRequestMillis)
        }

        metricsTracker.trackNetworkCall(MetricsType.Network.SdkInit, configApiRequestMillis)

        return configApiResult
    }

    override fun deinitialize() {
        CXLogger.i(TAG, "Deinitializing SDK")
        ResolvedEndpoints.reset()
        ClickCounterTracker.reset()
        config = null
        factories = null
        adFactory = null
        metricsTracker.stop()
        TrackingFieldResolver.clear()
    }

    // TODO. Replace with LazyAdapterResolver
    private suspend fun resolveAdapters(config: Config): BidAdNetworkFactories =
        withContext(Dispatchers.IO) {
            val factories = adapterResolver.resolveBidAdNetworkFactories(
                forTheseNetworks = config.bidders.map {
                    it.key
                }.toSet()
            )

            this@InitializationServiceImpl.factories = factories
            factories
        }

    private fun initAdFactory(appKey: String, config: Config, factories: BidAdNetworkFactories) {
        adFactory = AdFactory(
            appKey,
            config,
            factories,
            metricsTracker,
            eventTracker,
            winLossTracker,
            ConnectionStatusService(),
        )
    }

    private suspend fun initializeAdapterNetworks(config: Config, context: Context) {
        val adapterInitializers = factories?.initializers ?: return

        // Initialize only available adapters and if init config is present for them.

        config.bidders.onEach { bidderCfg ->

            val initializer = adapterInitializers[bidderCfg.key]

            if (initializer == null) {
                CXLogger.w(TAG, "No initializer found for ${bidderCfg.key}")
                return@onEach
            }

            adapterInitializers[bidderCfg.key]?.initialize(
                context,
                bidderCfg.value.initData,
                privacyService.cloudXPrivacy
            )
        }
    }

    private suspend fun sendInitSDKEvent(cfg: Config, appKey: String) {
        val deviceInfo = provideDeviceInfo()
        val sdkVersion = BuildConfig.SDK_VERSION_NAME
        val deviceType = if (deviceInfo.isTablet) "tablet" else "mobile"
        val sessionId = cfg.sessionId

        TrackingFieldResolver.setSessionConstData(
            sessionId,
            sdkVersion,
            deviceType,
            ResolvedEndpoints.testGroupName,
            appInfoProvider().packageName
        )
        TrackingFieldResolver.setConfig(cfg)

        val eventId = UUID.randomUUID().toString()
        val bidRequestParams = BidRequestProvider.Params(
            placementId = "",
            adType = AdType.Banner.Standard,
            placementName = "",
            accountId = cfg.accountId ?: "",
            appKey = appKey,
            appId = cfg.appId ?: ""
        )
        val bidRequestProvider = BidRequestProvider(
            emptyMap()
        )
        val bidRequestParamsJson = bidRequestProvider.invoke(bidRequestParams, eventId)
        TrackingFieldResolver.setRequestData(eventId, bidRequestParamsJson)

        val payload = TrackingFieldResolver.buildPayload(eventId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            basePayload = payload.replace(eventId, ARG_PLACEHOLDER_EVENT_ID)
            crashReportingService.setBasePayload(basePayload)
            metricsTracker.setBasicData(sessionId, accountId, basePayload)

            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.SDK_INIT)
        }
    }


    companion object {
        private const val ARG_PLACEHOLDER_EVENT_ID = "{eventId}"
    }
}