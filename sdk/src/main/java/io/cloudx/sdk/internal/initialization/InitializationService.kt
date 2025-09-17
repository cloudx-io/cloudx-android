package io.cloudx.sdk.internal.initialization

import android.content.Context
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.crash.CrashReportingService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.imp_tracker.win_loss.WinLossTracker
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
     * @return [Config] upon successful initialization, [CLXError] otherwise
     */
    suspend fun initialize(appKey: String): Result<Config, CLXError>

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
    crashReportingService: CrashReportingService = CrashReportingService()
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
        crashReportingService
    )