package io.cloudx.adapter.meta

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.AudienceNetworkAds
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Keep
internal object Initializer : CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult = withContext(Dispatchers.Main) {
        if (isInitialized) {
            CloudXLogger.d(TAG, "Meta SDK already initialized")
            return@withContext CloudXAdapterInitializationResult.Success
        }

        privacy.updatePrivacy()

        suspendCancellableCoroutine<CloudXAdapterInitializationResult> { continuation ->
            val initListener = AudienceNetworkAds.InitListener { initResult ->
                if (initResult.isSuccess) {
                    CloudXLogger.d(
                        TAG,
                        "Meta SDK successfully finished initialization: ${initResult.message}"
                    )
                    isInitialized = true

                    // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                    // So we have a try catch block around it.
                    try {
                        continuation.resume(CloudXAdapterInitializationResult.Success)
                    } catch (e: Exception) {
                        CloudXLogger.w(TAG, "Continuation resumed more than once", e)
                    }
                } else {
                    CloudXLogger.e(
                        TAG,
                        "Meta SDK failed to finish initialization: ${initResult.message}"
                    )
                    continuation.resume(CloudXAdapterInitializationResult.Error(initResult.message))
                }
            }

            val placementIds = serverExtras.getPlacementIds()
            if (placementIds.isEmpty()) {
                CloudXLogger.w(TAG, "No placement IDs found for Meta adapter initialization")
            }

            CloudXLogger.d(
                TAG,
                "Initializing Meta Audience Network SDK with placement IDs: $placementIds"
            )
            AudienceNetworkAds.buildInitSettings(context)
                .withMediationService("CLOUDX")
                .withPlacementIds(placementIds)
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