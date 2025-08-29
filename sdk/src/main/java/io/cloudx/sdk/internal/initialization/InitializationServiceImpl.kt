package io.cloudx.sdk.internal.initialization

import android.content.Context
import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.adfactory.AdFactory
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.bid.BidRequestProvider
import io.cloudx.sdk.internal.common.service.ActivityLifecycleService
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.config.ResolvedEndpoints
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.core.resolver.AdapterFactoryResolver
import io.cloudx.sdk.internal.core.resolver.BidAdNetworkFactories
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.exception.SdkCrashHandler
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.EventType
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.util.normalizeAndHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.system.measureTimeMillis
import androidx.core.content.edit
import com.xor.XorEncryption
import io.cloudx.sdk.CloudXErrorCodes
import io.cloudx.sdk.internal.imp_tracker.ClickCounterTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.kill_switch.KillSwitch

/**
 * Initialization service impl - initializes CloudX SDK; ignores all the following init calls after successful initialization.
 */
internal class InitializationServiceImpl(
    context: Context,
    private val configApi: ConfigApi,
    private val provideConfigRequest: ConfigRequestProvider,
    private val adapterResolver: AdapterFactoryResolver,
    private val privacyService: PrivacyService,
    private val _metricsTrackerNew: MetricsTrackerNew,
    private val eventTracker: EventTracker,
    private val provideAppInfo: AppInfoProvider,
    private val provideDeviceInfo: DeviceInfoProvider,
    private val geoApi: GeoApi
) : InitializationService {

    private val context: Context = context.applicationContext
    override val initialized: Boolean
        get() = config != null

    private var config: Config? = null
    private var appKey: String = ""
    private var basePayload: String = ""

    private val mutex = Mutex()

    fun isSdkRelatedError(throwable: Throwable): Boolean {
        return throwable.stackTrace.any { it.className.startsWith("io.cloudx.sdk") }
    }

    private fun registerSdkCrashHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current !is SdkCrashHandler) {  // <---- Only set if not already set by us
            Thread.setDefaultUncaughtExceptionHandler(
                SdkCrashHandler { thread, throwable ->
                    if (!isSdkRelatedError(throwable)) return@SdkCrashHandler

                    config?.let {
                        val sessionId = it.sessionId
                        val errorMessage = throwable.message
                        val stackTrace = throwable.stackTraceToString()

                        val pendingReport = PendingCrashReport(
                            sessionId = sessionId,
                            errorMessage = errorMessage ?: "Unknown error",
                            errorDetails = stackTrace,
                            basePayload = basePayload,
                        )

                        savePendingCrashReport(pendingReport)
                    }
                }
            )
        }
    }

    override val metricsTrackerNew: MetricsTrackerNew
        get() = _metricsTrackerNew

    data class PendingCrashReport(
        val sessionId: String,
        val errorMessage: String,
        val errorDetails: String,
        val basePayload: String
    )

    private fun PendingCrashReport.toJson(): JSONObject {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("errorMessage", errorMessage)
            put("errorDetails", errorDetails)
            put("basePayload", basePayload)
        }
    }

    private fun savePendingCrashReport(report: PendingCrashReport) {
        val json = report.toJson().toString()
        val prefs = context.getSharedPreferences("cloudx_crash_store", Context.MODE_PRIVATE)
        prefs.edit(commit = true) { putString("pending_crash", json) }
    }

    private fun getPendingCrashIfAny(): PendingCrashReport? {
        val prefs = context.getSharedPreferences("cloudx_crash_store", Context.MODE_PRIVATE)
        val pendingJson = prefs?.getString("pending_crash", null) ?: return null

        val pending = JSONObject(pendingJson).let { json ->
            PendingCrashReport(
                sessionId = json.getString("sessionId"),
                errorMessage = json.getString("errorMessage"),
                errorDetails = json.getString("errorDetails"),
                basePayload = json.getString("basePayload")
            )
        }

        prefs.edit(commit = true) { remove("pending_crash") }

        return pending
    }

    override suspend fun initialize(appKey: String): Result<Config, Error> =
        mutex.withLock {
            this.appKey = appKey

            registerSdkCrashHandler()

            // If we already saw SDK_DISABLED this session, fail fast
            if (KillSwitch.sdkDisabledForSession) {
                CloudXLogger.warn("InitializationServiceImpl", "Init skipped: SDK disabled for session.")
                return Result.Failure(
                    Error(
                        description = "SDK disabled by server (traffic control)",
                        errorCode = CloudXErrorCodes.INIT_SDK_DISABLED
                    )
                )
            }

            val config = this.config
            if (config != null) {
                return Result.Success(config)
            }

            val configApiResult: Result<Config, Error>
            val configApiRequestMillis = measureTimeMillis {
                configApiResult = configApi.invoke(appKey, provideConfigRequest())
            }

            if (configApiResult is Result.Success) {
                val cfg = configApiResult.value
                this.config = cfg

                eventTracker.setEndpoint(cfg.trackingEndpointUrl)
                eventTracker.trySendingPendingTrackingEvents()

                ResolvedEndpoints.resolveFrom(cfg)
                SdkKeyValueState.setKeyValuePaths(cfg.keyValuePaths)

                metricsTrackerNew.start(cfg)

                val geoDataResult: Result<Map<String, String>, Error>
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
                    CloudXLogger.info("MainActivity", "User Geo IP: $userGeoIp")
                    CloudXLogger.info("MainActivity", "Hashed Geo IP: $hashedGeoIp")
                    TrackingFieldResolver.setHashedGeoIp(hashedGeoIp)

                    CloudXLogger.info("MainActivity", "geo data: $geoInfo")
                    GeoInfoHolder.setGeoInfo(geoInfo, headersMap)

                    val removePii = privacyService.shouldClearPersonalData()
                    CloudXLogger.info("MainActivity", "PII remove: $removePii")

                    sendInitSDKEvent(cfg, appKey)

                    val pendingCrash = getPendingCrashIfAny()
                    pendingCrash?.let {
                        sendErrorEvent(it)
                    }
                }

                val factories = resolveAdapters(cfg)

                val appKeyOverride = cfg.appKeyOverride ?: appKey
                initAdFactory(appKeyOverride, cfg, factories)
                initializeAdapterNetworks(cfg, context)

                metricsTrackerNew.trackNetworkCall(MetricsType.Network.GeoApi, geoRequestMillis)
            } else if (configApiResult is Result.Failure && configApiResult.value.errorCode == CloudXErrorCodes.INIT_SDK_DISABLED) {
                KillSwitch.sdkDisabledForSession = true
                CloudXLogger.warn("InitializationServiceImpl", "SDK disabled for session by server.")
            }

            metricsTrackerNew.trackNetworkCall(MetricsType.Network.SdkInit, configApiRequestMillis)

            configApiResult
        }

    override fun deinitialize() {
        ResolvedEndpoints.reset()
        ClickCounterTracker.reset()
        config = null
        factories = null
        adFactory = null
        metricsTrackerNew.stop()
        TrackingFieldResolver.clear()
    }

    private var factories: BidAdNetworkFactories? = null
    override var adFactory: AdFactory? = null
        private set

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
            metricsTrackerNew,
            eventTracker,
            ConnectionStatusService(),
            AppLifecycleService(),
            ActivityLifecycleService()
        )
    }

    private suspend fun initializeAdapterNetworks(config: Config, context: Context) {
        val adapterInitializers = factories?.initializers ?: return

        // Initialize only available adapters and if init config is present for them.

        config.bidders.onEach { bidderCfg ->

            val initializer = adapterInitializers[bidderCfg.key]

            if (initializer == null) {
                CloudXLogger.warn(
                    "InitializationServiceImpl",
                    "No initializer found for ${bidderCfg.key}"
                )
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
        val sessionId = cfg.sessionId + UUID.randomUUID().toString()

        TrackingFieldResolver.setSessionConstData(
            sessionId,
            sdkVersion,
            deviceType,
            ResolvedEndpoints.testGroupName
        )
        TrackingFieldResolver.setConfig(cfg)

        val eventId = UUID.randomUUID().toString()
        val bidRequestParams = BidRequestProvider.Params(
            adId = "",
            adType = AdType.Banner.Standard,
            placementName = "",
            accountId = cfg.accountId ?: "",
            appKey = appKey
        )
        val bidRequestProvider = BidRequestProvider(
            context,
            emptyMap()
        )
        val bidRequestParamsJson = bidRequestProvider.invoke(bidRequestParams, eventId)
        TrackingFieldResolver.setRequestData(eventId, bidRequestParamsJson)

        val payload = TrackingFieldResolver.buildPayload(eventId)
        val accountId = TrackingFieldResolver.getAccountId()

        if (payload != null && accountId != null) {
            basePayload = payload.replace(eventId, ARG_PLACEHOLDER_EVENT_ID)
            metricsTrackerNew.setBasicData(sessionId, accountId, basePayload)

            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.SDK_INIT)
        }
    }

    private fun sendErrorEvent(
        pendingCrashReport: PendingCrashReport
    ) {
        val eventId = UUID.randomUUID().toString()

        var payload = if (pendingCrashReport.basePayload.isEmpty()) {
            basePayload.replace(ARG_PLACEHOLDER_EVENT_ID, eventId)
        } else {
            pendingCrashReport.basePayload.replace(ARG_PLACEHOLDER_EVENT_ID, eventId)
        }

        payload = payload.plus(";")
            .plus(pendingCrashReport.errorMessage).plus(";")
            .plus(pendingCrashReport.errorDetails)

        val accountId = TrackingFieldResolver.getAccountId()

        if (accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.SDK_ERROR)
        }
    }

    companion object {
        private const val ARG_PLACEHOLDER_EVENT_ID = "{eventId}"
    }
}