package io.cloudx.sdk.internal.initialization

import android.content.Context
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.adfactory.AdFactory
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequestProvider
import io.cloudx.sdk.internal.core.resolver.AdapterFactoryResolver
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.cloudx.sdk.internal.privacy.PrivacyService

/**
 * Initialization service - responsible for all CloudX initialization related things, notably - configuration fetching.
 */
internal interface InitializationService {

    /**
     * Initialized status of CloudX SDK
     */
    val initialized: Boolean

    /**
     * Initialize CloudX SDK
     * @param appKey - unique application key/identifier; comes from app's Publisher.
     * @return [Config] upon successful initialization, [CLXError] otherwise
     */
    suspend fun initialize(appKey: String): Result<Config, CLXError>

    /**
     * Ad factory - null when SDK is not [initialized]
     */
    val adFactory: AdFactory?

    val metricsTrackerNew: MetricsTrackerNew?

    fun deinitialize()
}

internal fun InitializationService(
    context: Context,
    configApi: ConfigApi,
    provideConfigRequest: ConfigRequestProvider = ConfigRequestProvider(),
    adapterFactoryResolver: AdapterFactoryResolver = AdapterFactoryResolver(),
    privacyService: PrivacyService = PrivacyService(),
    metricsTrackerNew: MetricsTrackerNew = MetricsTrackerNew(),
    eventTracker: EventTracker = EventTracker(),
    appInfoProvider: AppInfoProvider = AppInfoProvider(),
    deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(),
    geoApi: GeoApi = GeoApi()
): InitializationService =
    InitializationServiceImpl(
        context,
        configApi,
        provideConfigRequest,
        adapterFactoryResolver,
        privacyService,
        metricsTrackerNew,
        eventTracker,
        appInfoProvider,
        deviceInfoProvider,
        geoApi
    )