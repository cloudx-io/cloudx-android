package io.cloudx.adapter.mintegral

import android.content.Context
import com.mbridge.msdk.MBridgeConstans
import com.mbridge.msdk.foundation.same.net.Aa
import com.mbridge.msdk.out.MBConfiguration
import com.mbridge.msdk.out.MBridgeSDKFactory
import com.mbridge.msdk.out.SDKInitStatusListener
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal object Initializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult =
        withContext(Dispatchers.Main) {
            if (isInitialized) {
                CloudXLogger.d(TAG, "already initialized")
                CloudXAdapterInitializationResult.Success
            } else {
                privacy.updateMintegralPrivacy(context)

                if (!trySetMintegralChannelCode()) {
                    return@withContext CloudXAdapterInitializationResult.Error()
                }

                suspendCancellableCoroutine<CloudXAdapterInitializationResult> { continuation ->
                    val sdk = MBridgeSDKFactory.getMBridgeSDK()
                    val map = sdk.getMBConfigurationMap(
                        config["appID"] ?: "",
                        config["appKey"] ?: ""
                    )

                    sdk.init(map, context, object : SDKInitStatusListener {
                        override fun onInitFail(p0: String?) {
                            CloudXLogger.d(TAG, "init fail: $p0")
                            // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                            // So we have a try catch block around it.
                            try {
                                continuation.resume(CloudXAdapterInitializationResult.Error(p0 ?: ""))
                            } catch (e: Exception) {
                                CloudXLogger.e(TAG, e.toString())
                            }
                        }

                        override fun onInitSuccess() {
                            isInitialized = true
                            CloudXLogger.d(TAG, "initialized")
                            // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                            // So we have a try catch block around it.
                            try {
                                continuation.resume(CloudXAdapterInitializationResult.Success)
                            } catch (e: Exception) {
                                CloudXLogger.e(TAG, e.toString())
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

    CloudXLogger.d(TAG, "setting EU consent: ${value.isUserConsent}; coppa: ${value.isAgeRestrictedUser}")

    privacy.isUserConsent?.let {
        sdk.setConsentStatus(
            context,
            if (it) MBridgeConstans.IS_SWITCH_ON else MBridgeConstans.IS_SWITCH_OFF
        )
    }

    // TODO: Mintegral GPP/CPPA integration

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
    CloudXLogger.e(TAG, "failed to set mintegral's channel code: $e")
    false
}