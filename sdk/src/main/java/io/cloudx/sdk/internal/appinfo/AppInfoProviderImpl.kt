package io.cloudx.sdk.internal.appinfo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.cloudx.sdk.internal.CloudXLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppInfoProviderImpl(
    private val appContext: Context
) : AppInfoProvider {

    private val tag = "AppInfoProviderImpl"

    private var appInfo: AppInfo? = null

    // Possible race-conditions: appInfo might get updated a few times in the worst case scenario.
    override suspend fun invoke(): AppInfo {
        val appInfo = this.appInfo
        if (appInfo != null) {
            return appInfo
        }

        val newAppInfo = try {
            withContext(Dispatchers.IO) {
                with(appContext) {
                    val pckgInfo = appContext.getPackageInfoCompat()
                    AppInfo(
                        appName = packageManager.getApplicationLabel(applicationInfo).toString(),
                        pckgInfo.packageName,
                        pckgInfo.versionName ?: "none"
                    )
                }
            }
        } catch (e: Exception) {
            CloudXLogger.e(tag, e.toString())
            AppInfo("", "", "")
        }

        this.appInfo = newAppInfo
        return newAppInfo
    }
}

private fun Context.getPackageInfoCompat(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}