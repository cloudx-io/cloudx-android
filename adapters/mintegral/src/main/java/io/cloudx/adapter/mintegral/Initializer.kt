package io.cloudx.adapter.mintegral

import android.app.Activity
import android.content.Context
import com.mbridge.msdk.MBridgeConstans
import com.mbridge.msdk.foundation.same.net.Aa
import com.mbridge.msdk.out.MBConfiguration
import com.mbridge.msdk.out.MBridgeSDKFactory
import com.mbridge.msdk.out.SDKInitStatusListener
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.AdNetworkInitializer
import io.cloudx.sdk.internal.adapter.InitializationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal object Initializer : AdNetworkInitializer {

    override suspend fun initialize(
        activity: Activity,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): InitializationResult =
        withContext(Dispatchers.Main) {
            if (isInitialized) {
                CloudXLogger.debug(TAG, "already initialized")
                InitializationResult.Success
            } else {
                val app = activity.applicationContext

                privacy.updateMintegralPrivacy(app)

                if (!trySetMintegralChannelCode()) {
                    return@withContext InitializationResult.Error()
                }

                suspendCancellableCoroutine<InitializationResult> { continuation ->
                    val sdk = MBridgeSDKFactory.getMBridgeSDK()
                    val map = sdk.getMBConfigurationMap(
                        config["appID"] ?: "",
                        config["appKey"] ?: ""
                    )

                    sdk.init(map, app, object : SDKInitStatusListener {
                        override fun onInitFail(p0: String?) {
                            CloudXLogger.debug(TAG, "init fail: $p0")
                            // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                            // So we have a try catch block around it.
                            try {
                                continuation.resume(InitializationResult.Error(p0 ?: ""))
                            } catch (e: Exception) {
                                CloudXLogger.error(TAG, e.toString())
                            }
                        }

                        override fun onInitSuccess() {
                            isInitialized = true
                            CloudXLogger.debug(TAG, "initialized")
                            // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                            // So we have a try catch block around it.
                            try {
                                continuation.resume(InitializationResult.Success)
                            } catch (e: Exception) {
                                CloudXLogger.error(TAG, e.toString())
                            }
                        }
                    })
                }
            }
        }
}

private var isInitialized = false

private const val TAG = "MintegralInitializer"

internal const val MintegralVersion = MBConfiguration.SDK_VERSION

// More info: https://dev.mintegral.com/doc/index.html?file=sdk-m_sdk-android&lang=en#sdkprivacycompliancestatement
private fun StateFlow<CloudXPrivacy>.updateMintegralPrivacy(context: Context) {
    val privacy = value
    val sdk = MBridgeSDKFactory.getMBridgeSDK()

    CloudXLogger.debug(TAG, "setting EU consent: ${value.isUserConsent}; US do not sell: ${value.isDoNotSell} coppa: ${value.isAgeRestrictedUser}")

    privacy.isUserConsent?.let {
        sdk.setConsentStatus(
            context,
            if (it) MBridgeConstans.IS_SWITCH_ON else MBridgeConstans.IS_SWITCH_OFF
        )
    }

    privacy.isDoNotSell?.let {
        sdk.setDoNotTrackStatus(context, it)
    }

    privacy.isAgeRestrictedUser?.let {
        sdk.setCoppaStatus(context, it)
    }
}

/**
 * Mintegral:
 *
 * Additionally, before calling the SDK initialization API during the integration of our SDK, you need to call the following code.
 * @return fail/success status
 * @see "SDK-317"
 */
private fun trySetMintegralChannelCode(): Boolean = try {
    val channelCode = "Y+H6DFttYrPQYcIAicKwJQKQYrN="
    val a = Aa()
    val c = a::class.java
    val method = c.getDeclaredMethod("b", String::class.java)
    method.isAccessible = true
    method.invoke(a, channelCode)
    true
} catch (e: Exception) {
    CloudXLogger.error(TAG, "failed to set mintegral's channel code: $e")
    false
}