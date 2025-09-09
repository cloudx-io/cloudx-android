package io.cloudx.sdk.mocks

import android.os.Bundle
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.config.ConfigRequest
import java.util.UUID

internal class MockConfigAPIWithPredefinedConfig : ConfigApi {

    override suspend fun invoke(
        appKey: String,
        configRequest: ConfigRequest
    ): Result<Config, CLXError> = Result.Success(
        Config(
            precacheSize = 5,
            auctionEndpointUrl = Config.EndpointConfig("nopoint"),
            cdpEndpointUrl = Config.EndpointConfig(""),
            sessionId = "mock-sessionid-${UUID.randomUUID()}",
            // To support mock bid network adapter implementation.
            bidders = mapOf(AdNetwork.CloudX.let { bidderName ->
                bidderName to Config.Bidder(bidderName, Bundle.EMPTY)
            }),
            trackingEndpointUrl = "nopoint",
            organizationId = "",
            accountId = "",
            geoDataEndpointUrl = "nopoint",
            geoHeaders = emptyList(),
            trackers = emptyList(),
            appKeyOverride = "",
            rawJson = null,
            keyValuePaths = null,
            metrics = null,
            placements = mapOf(
                "defaultInterstitial".let { placementName ->
                    placementName to Config.Placement.Interstitial(
                        id = placementName,
                        name = placementName,
                        bidResponseTimeoutMillis = 5000,
                        adLoadTimeoutMillis = 5000
                    )
                },
                "defaultRewarded".let { placementName ->
                    placementName to Config.Placement.Rewarded(
                        id = placementName,
                        name = placementName,
                        bidResponseTimeoutMillis = 5000,
                        adLoadTimeoutMillis = 5000
                    )
                },
                "defaultBanner".let { placementName ->
                    placementName to Config.Placement.Banner(
                        id = placementName,
                        name = placementName,
                        bidResponseTimeoutMillis = 5000,
                        adLoadTimeoutMillis = 5000,
                        refreshRateMillis = 15000,
                    )
                }
            )
        )
    )
}