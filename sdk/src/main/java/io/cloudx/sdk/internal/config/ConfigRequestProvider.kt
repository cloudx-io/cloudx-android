package io.cloudx.sdk.internal.config

import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.gaid.GAIDProvider

// Lazy singleton
private val LazySingleInstance by lazy {
    ConfigRequestProvider(
        io.cloudx.sdk.BuildConfig.SDK_VERSION_NAME,
        AppInfoProvider(),
        DeviceInfoProvider(),
        GAIDProvider()
    )
}

// Factory function
internal fun ConfigRequestProvider(): ConfigRequestProvider = LazySingleInstance

// Main class
internal class ConfigRequestProvider(
    private val sdkVersion: String,
    private val provideAppInfo: AppInfoProvider,
    private val provideDeviceInfo: DeviceInfoProvider,
    private val provideGAID: GAIDProvider
) {

    suspend operator fun invoke(): ConfigRequest {
        val deviceInfo = provideDeviceInfo()
        val gaidData = provideGAID()

        return ConfigRequest(
            bundle = provideAppInfo().packageName,
            os = deviceInfo.os,
            osVersion = deviceInfo.osVersion,
            deviceModel = deviceInfo.model,
            deviceManufacturer = deviceInfo.manufacturer,
            sdkVersion = sdkVersion,
            gaid = gaidData.gaid,
            dnt = gaidData.isLimitAdTrackingEnabled
        )
    }
}
