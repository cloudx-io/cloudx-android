package io.cloudx.sdk.internal.initialization

import android.content.Context
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.crash.CrashReportingService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.util.Result

/**
 * Initialization service - responsible for all CloudX initialization related things, notably - configuration fetching.
 */
internal interface InitializationService {

    val adFactory: AdFactory?

    val metricsTracker: MetricsTracker?

    /**
     * Initialize CloudX SDK
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @return [Config] upon successful initialization, [CloudXError] otherwise
     */
    suspend fun initialize(appKey: String): Result<Config, CloudXError>

    fun deinitialize()
}

internal fun InitializationService(
    context: Context = ApplicationContext(),
    configApi: ConfigApi,
    provideConfigRequest: ConfigRequestProvider = ConfigRequestProvider(),
    adapterFactoryResolver: AdapterFactoryResolver = AdapterFactoryResolver(),
    privacyService: PrivacyService = PrivacyService(),
    metricsTracker: MetricsTracker = MetricsTracker(),
    eventTracker: EventTracker = EventTracker(),
    winLossTracker: WinLossTracker = WinLossTracker(),
    deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(),
    geoApi: GeoApi = GeoApi(),
    crashReportingService: CrashReportingService = CrashReportingService(),
    appInfoProvider: AppInfoProvider = AppInfoProvider()
): InitializationService =
    InitializationServiceImpl(
        context,
        configApi,
        provideConfigRequest,
        adapterFactoryResolver,
        privacyService,
        metricsTracker,
        eventTracker,
        winLossTracker,
        deviceInfoProvider,
        geoApi,
        crashReportingService,
        appInfoProvider
    )