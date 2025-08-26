package io.cloudx.adapter.meta

import android.content.Context
import com.facebook.ads.AudienceNetworkAds
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.InitializationResult
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
    ): InitializationResult = withContext(Dispatchers.Main) {
        if (isInitialized) {
            log(TAG, "Meta SDK already initialized")
            return@withContext InitializationResult.Success
        }

        privacy.updatePrivacy()

        suspendCancellableCoroutine<InitializationResult> { continuation ->
            val initListener = AudienceNetworkAds.InitListener { initResult ->
                if (initResult.isSuccess) {
                    log(TAG, "Meta SDK successfully finished initialization: ${initResult.message}")
                    isInitialized = true

                    // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                    // So we have a try catch block around it.
                    try {
                        continuation.resume(InitializationResult.Success)
                    } catch (e: Exception) {
                        log(TAG, "Continuation resumed more than once: ${e.message}")
                    }
                } else {
                    log(TAG, "Meta SDK failed to finish initialization: ${initResult.message}")
                    continuation.resume(InitializationResult.Error(initResult.message))
                }
            }

            AudienceNetworkAds.buildInitSettings(context)
                .withMediationService("")
                .withInitListener(initListener)
                .initialize()
        }
    }
}

private var isInitialized = false

private const val TAG = "MetaAdapterInitializer"

internal const val AudienceNetworkAdsVersion = BuildConfig.AUDIENCE_SDK_VERSION_NAME

private fun StateFlow<CloudXPrivacy>.updatePrivacy() {
    val cloudxPrivacy = value
    // TODO. https://developers.facebook.com/docs/audience-network/optimization/best-practices/coppa
    // AdSettings.setMixedAudience(cloudxPrivacy.isAgeRestrictedUser )

    // TODO. CCPA. https://developers.facebook.com/docs/audience-network/optimization/best-practices/data-processing-options
    // AdSettings.setDataProcessingOptions()
}