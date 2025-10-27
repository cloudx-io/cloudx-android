package io.cloudx.adapter.meta

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import com.facebook.ads.AudienceNetworkAds
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.adapter.CloudXAdapterInitializer
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.toFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@Keep
internal object Initializer : CloudXAdapterInitializer {

    private val logger = CXLogger.forComponent("MetaAdapterInitializer")

    override suspend fun initialize(
        context: Context,
        serverExtras: Bundle,
        privacy: StateFlow<CloudXPrivacy>
    ): Result<Unit, CloudXError> = withContext(Dispatchers.Main) {
        if (isInitialized) {
            logger.d("Meta SDK already initialized")
            return@withContext Unit.toSuccess()
        }

        suspendCancellableCoroutine { continuation ->
            val initListener = AudienceNetworkAds.InitListener { initResult ->
                if (initResult.isSuccess) {
                    logger.d("Meta SDK successfully finished initialization: ${initResult.message}")
                    isInitialized = true

                    // Sometimes adapters call [Continuation.resume] twice which they shouldn't.
                    // So we have a try catch block around it.
                    try {
                        continuation.resume(Unit.toSuccess())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.w("Continuation resumed more than once", e)
                    }
                } else {
                    logger.e("Meta SDK failed to finish initialization: ${initResult.message}")
                    continuation.resume(
                        CloudXErrorCode.ADAPTER_INITIALIZATION_ERROR.toFailure(message = initResult.message)
                    )
                }
            }

            val metaPlacementIds = serverExtras.getMetaPlacementIds()
            if (metaPlacementIds.isEmpty()) {
                logger.w("No Meta placement IDs found for Meta adapter initialization")
            }

            logger.d("Initializing Meta Audience Network SDK with placement IDs: $metaPlacementIds")
            AudienceNetworkAds.buildInitSettings(context)
                .withMediationService("CLOUDX")
                .withPlacementIds(metaPlacementIds)
                .withInitListener(initListener)
                .initialize()
        }
    }
}

private var isInitialized = false

internal const val AudienceNetworkAdsVersion = BuildConfig.AUDIENCE_SDK_VERSION_NAME
