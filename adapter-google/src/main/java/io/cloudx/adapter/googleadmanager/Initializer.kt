package io.cloudx.adapter.googleadmanager

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal object Initializer: CloudXAdapterInitializer {

    override suspend fun initialize(
        context: Context,
        config: Map<String, String>,
        privacy: StateFlow<CloudXPrivacy>
    ): CloudXAdapterInitializationResult =
        withContext(Dispatchers.Main) {
            if (isInitialized) {
                CloudXLogger.debug(TAG, "already initialized")
                CloudXAdapterInitializationResult.Success
            } else {
                privacy.updateAdManagerPrivacy()

                suspendCancellableCoroutine { continuation ->
                    MobileAds.initialize(context) {
                        isInitialized = true
                        CloudXLogger.debug(TAG, "initialized")
                        // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                        // So we have a try catch block around it.
                        try {
                            continuation.resume(CloudXAdapterInitializationResult.Success)
                        } catch (e: Exception) {
                            CloudXLogger.error(TAG, e.toString())
                        }
                    }
                }
            }
        }
}

private var isInitialized = false

private const val TAG = "GoogleAdManagerInitializer"

internal val AdManagerVersion = MobileAds.getVersion().toString()

private fun StateFlow<CloudXPrivacy>.updateAdManagerPrivacy() {
    val isAgeRestrictedUser = value.isAgeRestrictedUser

    CloudXLogger.debug(TAG, "setting isAgeRestrictedUser: $isAgeRestrictedUser for AdManager SDK")

    val requestConfiguration = MobileAds.getRequestConfiguration().toBuilder().apply {
        setTagForUnderAgeOfConsent(
            when (isAgeRestrictedUser) {
                true -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                false -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
                null -> RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED
            }
        )
        setTagForChildDirectedTreatment(
            when (isAgeRestrictedUser) {
                true -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                false -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                null -> RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED
            }
        )
    }.build()

    MobileAds.setRequestConfiguration(requestConfiguration)
}