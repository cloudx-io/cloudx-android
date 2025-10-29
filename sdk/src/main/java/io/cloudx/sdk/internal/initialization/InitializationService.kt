package io.cloudx.sdk.internal.initialization

import android.content.Context
import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.tracker.ClickCounterTracker
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.SessionMetricsTracker
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.tracker.crash.CrashReportingService
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsType
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.normalizeAndHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Initialization service - responsible for all CloudX initialization related things, notably - configuration fetching.
 * Initializes CloudX SDK; ignores all the following init calls after successful initialization.
 */
internal class InitializationService(
    private val context: Context = ApplicationContext(),
    private val configApi: ConfigApi,
    private val provideConfigRequest: ConfigRequestProvider = ConfigRequestProvider(),
    private val adapterResolver: AdapterFactoryResolver = AdapterFactoryResolver(),
    private val privacyService: PrivacyService = PrivacyService(),
    private val _metricsTracker: MetricsTracker = MetricsTracker(),
    private val eventTracker: EventTracker = EventTracker(),
    private val winLossTracker: WinLossTracker = WinLossTracker(),
    private val provideDeviceInfo: DeviceInfoProvider = DeviceInfoProvider(),
    private val geoApi: GeoApi = GeoApi(),
    private val crashReportingService: CrashReportingService = CrashReportingService(),
    private val errorReportingService: ErrorReportingService = ErrorReportingService(),
    private val appInfoProvider: AppInfoProvider = AppInfoProvider()
) {

    private val logger = CXLogger.forComponent("InitializationService")

    private var config: Config? = null
    private var appKey: String = ""
    private var basePayload: String = ""
    private var factories: BidAdNetworkFactories? = null

    val metricsTracker: MetricsTracker
        get() = _metricsTracker

    var adFactory: AdFactory? = null
        private set

    /**
     * Initialize CloudX SDK
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @return [Config] upon successful initialization, [CloudXError] otherwise
     */
    suspend fun initialize(appKey: String): Result<Config, CloudXError> {
        logger.i("Starting SDK initialization with appKey: $appKey")
        this.appKey = appKey

        crashReportingService.registerSdkCrashHandler()

        val config = this.config
        if (config != null) {
            logger.i("SDK already initialized, returning cached config")
            return Result.Success(config)
        }

        val configApiResult: Result<Config, CloudXError>
        val configApiRequestMillis = measureTimeMillis {
            configApiResult = configApi.invoke(appKey, provideConfigRequest())
        }

        if (configApiResult is Result.Failure) {
            if (configApiResult.value.code == CloudXErrorCode.SDK_DISABLED) {
                logger.w(
                    "SDK disabled by server via traffic control (0%). No ads this session."
                )
            }
            return configApiResult
        }

        if (configApiResult is Result.Success) {
            val cfg = configApiResult.value
            this.config = cfg
            crashReportingService.setConfig(cfg)

            TrackingFieldResolver.setSessionConstData(
                sessionId = cfg.sessionId,
                sdkVersion = BuildConfig.SDK_VERSION_NAME,
                deviceTypeName = if (provideDeviceInfo().isTablet) "tablet" else "phone",
                deviceTypeCode = if (provideDeviceInfo().isTablet) 5 else 4,
                abTestGroup = ResolvedEndpoints.testGroupName,
                appBundle = appInfoProvider().packageName
            )
            TrackingFieldResolver.setConfig(cfg)

            eventTracker.setEndpoint(cfg.trackingEndpointUrl)
            eventTracker.trySendingPendingTrackingEvents()

            winLossTracker.setAppKey(appKey)
            winLossTracker.setEndpoint(cfg.winLossNotificationUrl)
            winLossTracker.setPayloadMapping(cfg.winLossNotificationPayloadConfig)
            winLossTracker.trySendingPendingWinLossEvents()

            ResolvedEndpoints.resolveFrom(cfg)
            SdkKeyValueState.setKeyValuePaths(cfg.keyValuePaths)

            metricsTracker.start(cfg)
            SessionMetricsTracker.resetAll()

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
                logger.i("User Geo IP: $userGeoIp")
                logger.i("Hashed Geo IP: $hashedGeoIp")
                TrackingFieldResolver.setHashedGeoIp(hashedGeoIp)

                logger.i("geo data: $geoInfo")
                GeoInfoHolder.setGeoInfo(geoInfo, headersMap)

                val removePii = privacyService.shouldClearPersonalData()
                logger.i("PII remove: $removePii")

                sendInitSDKEvent(cfg, appKey)

                crashReportingService.sendPendingCrashIfAny()
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

    fun deinitialize() {
        logger.i("Deinitializing SDK")
        ResolvedEndpoints.reset()
        ClickCounterTracker.reset()
        config = null
        factories = null
        adFactory = null
        metricsTracker.stop()
        TrackingFieldResolver.clear()
        SessionMetricsTracker.resetAll()
    }

    // TODO. Replace with LazyAdapterResolver
    private suspend fun resolveAdapters(config: Config): BidAdNetworkFactories =
        withContext(Dispatchers.IO) {
            val factories = adapterResolver.resolveBidAdNetworkFactories(
                forTheseNetworks = config.bidders.map { it.key }.toSet()
            )

            this@InitializationService.factories = factories
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
                logger.w("No initializer found for ${bidderCfg.key}")
                return@onEach
            }

            try {
                adapterInitializers[bidderCfg.key]?.initialize(
                    context,
                    bidderCfg.value.initData,
                    privacyService.cloudXPrivacy
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Failed to initialize adapter for ${bidderCfg.key}", e)
            }
        }
    }

    private suspend fun sendInitSDKEvent(cfg: Config, appKey: String) {
        eventTracker.sendSdkInit(cfg, appKey) { basePayload ->
            this.basePayload = basePayload
            crashReportingService.setBasePayload(basePayload)
            errorReportingService.setBasePayload(basePayload)
            metricsTracker.setBasicData(cfg.sessionId, cfg.accountId ?: "", basePayload)
        }
    }

    companion object {
        private const val ARG_PLACEHOLDER_EVENT_ID = "{eventId}"
    }
}
