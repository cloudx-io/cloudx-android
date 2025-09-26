package io.cloudx.sdk.mocks

import io.cloudx.sdk.internal.appinfo.AppInfo
import io.cloudx.sdk.internal.appinfo.AppInfoProvider

internal object MockAppInfoProvider : AppInfoProvider {
    override suspend fun invoke(): AppInfo {
        val packageName = "io.cloudx.demo.app"
        return AppInfo(
            appName = packageName,
            packageName = packageName,
            appVersion = "0.0.1"
        )
    }
}