package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.adapter.CloudXAdapterBidRequestExtrasProvider
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.gaid.GAIDProvider
import io.cloudx.sdk.internal.httpclient.UserAgentProvider
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.screen.ScreenService
import org.json.JSONObject

// TODO. Separate Json conversion logic from business logic.
internal interface BidRequestProvider {

    suspend fun invoke(params: Params, auctionId: String): JSONObject

    class Params(
        val placementId: String,
        val adType: AdType,
        val placementName: String,
        val accountId: String,
        val appKey: String,
        val osVersionOld: Int? = null
    )
}

internal fun BidRequestProvider.Params.withEffectivePlacementId(): String {
    if (adType !is AdType.Interstitial && adType !is AdType.Rewarded) {
        PlacementLoopIndexTracker.getAndIncrement(placementName)
    }

    return placementId
}


internal fun BidRequestProvider(
    bidRequestExtrasProviders: Map<AdNetwork, CloudXAdapterBidRequestExtrasProvider>
) = BidRequestProviderImpl(
    ApplicationContext(),
    BuildConfig.SDK_VERSION_NAME,
    AppInfoProvider(),
    DeviceInfoProvider(),
    ScreenService(ApplicationContext()),
    ConnectionStatusService(),
    UserAgentProvider(),
    GAIDProvider(),
    PrivacyService(),
    bidRequestExtrasProviders
)