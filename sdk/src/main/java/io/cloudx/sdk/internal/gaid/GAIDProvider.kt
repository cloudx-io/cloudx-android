package io.cloudx.sdk.internal.gaid

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.cloudx.sdk.internal.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Lazy singleton
private val LazySingleInstance: GAIDProvider by lazy {
    GAIDProvider(
        ApplicationContext()
    )
}

// Factory function
internal fun GAIDProvider(): GAIDProvider = LazySingleInstance

// Main class
internal class GAIDProvider(
    private val context: Context
) {

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        runCatching {
            AdvertisingIdClient.getAdvertisingIdInfo(context)
        }.getOrNull()?.run {
            Result(
                gaid = if (isLimitAdTrackingEnabled) ANON_AD_ID else id ?: ANON_AD_ID,
                isLimitAdTrackingEnabled = isLimitAdTrackingEnabled
            )
        } ?: Result(
            gaid = ANON_AD_ID,
            isLimitAdTrackingEnabled = true
        )
    }

    /**
     * @property gaid - Google Advertising Id.
     * @property isLimitAdTrackingEnabled - Do Not track
     * @constructor Create empty Result
     */
    class Result(
        val gaid: String,
        val isLimitAdTrackingEnabled: Boolean
    )
}

private const val ANON_AD_ID = "00000000-0000-0000-0000-000000000000"
