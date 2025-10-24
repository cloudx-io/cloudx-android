package io.cloudx.sdk.internal.appinfo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

// Lazy singleton
private val LazySingleInstance by lazy {
    AppInfoProvider(
        ApplicationContext()
    )
}

// Factory function
internal fun AppInfoProvider(): AppInfoProvider = LazySingleInstance

// Main class
internal class AppInfoProvider(
    private val appContext: Context
) {

    private val logger = CXLogger.forComponent("AppInfoProvider")

    private var appInfo: AppInfo? = null

    // Possible race-conditions: appInfo might get updated a few times in the worst case scenario.
    suspend operator fun invoke(): AppInfo {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("Failed to retrieve app info", e)
            AppInfo("", "", "")
        }

        this.appInfo = newAppInfo
        return newAppInfo
    }
}

// Supporting types
internal class AppInfo(val appName: String, val packageName: String, val appVersion: String)

private fun Context.getPackageInfoCompat(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}
