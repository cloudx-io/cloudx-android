package io.cloudx.sdk.internal.deviceinfo

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.cloudx.sdk.R
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class DeviceInfoServiceImpl(
    private val appContext: Context
) : DeviceInfoProvider {

    private val tag = "DeviceInfoServiceImpl"

    private val isTablet: Boolean by lazy {
        appContext.resources.getBoolean(R.bool.isTablet)
    }

    override suspend fun invoke(): DeviceInfo {
        val mobileCarrier = try {
            withContext(Dispatchers.IO) {
                ContextCompat.getSystemService(
                    appContext, TelephonyManager::class.java
                )?.networkOperatorName ?: ""
            }
        } catch (e: Exception) {
            CXLogger.e(tag, e.toString())
            ""
        }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER ?: "",
            model = Build.MODEL ?: "",
            hwVersion = Build.HARDWARE ?: "",
            isTablet,
            os = "android",
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            language = Locale.getDefault().language,
            mobileCarrier,
            screenDensity = Resources.getSystem().displayMetrics.density,
        )
    }
}