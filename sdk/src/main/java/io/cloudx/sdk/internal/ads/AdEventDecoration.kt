package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.GlobalScopes
import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.InterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.RewardedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.banner.DecoratedBannerAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.DecoratedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.DecoratedRewardedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.EventType
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import kotlinx.coroutines.launch
import com.xor.XorEncryption
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.imp_tracker.ClickCounterTracker

private typealias Func = (() -> Unit)
private typealias ClickFunc = (() -> Unit)
private typealias ErrorFunc = ((error: CloudXAdapterError) -> Unit)

class AdEventDecoration(
    val onLoad: Func? = null,
    val onShow: Func? = null,
    val onHide: Func? = null,
    val onImpression: Func? = null,
    val onSkip: Func? = null,
    val onComplete: Func? = null,
    val onReward: Func? = null,
    val onClick: ClickFunc? = null,
    val onError: ErrorFunc? = null,
    val onDestroy: Func? = null,
    // TODO. Oof.
    val onStartLoad: Func? = null,
    val onTimeout: Func? = null,
) {

    operator fun plus(adEventDecoration: AdEventDecoration) = AdEventDecoration(
        {
            onLoad?.invoke()
            adEventDecoration.onLoad?.invoke()
        },
        {
            onShow?.invoke()
            adEventDecoration.onShow?.invoke()
        },
        {
            onHide?.invoke()
            adEventDecoration.onHide?.invoke()
        },
        {
            onImpression?.invoke()
            adEventDecoration.onImpression?.invoke()
        },
        {
            onSkip?.invoke()
            adEventDecoration.onSkip?.invoke()
        },
        {
            onComplete?.invoke()
            adEventDecoration.onComplete?.invoke()
        },
        {
            onReward?.invoke()
            adEventDecoration.onReward?.invoke()
        },
        {
            onClick?.invoke()
            adEventDecoration.onClick?.invoke()
        },
        {
            onError?.invoke(it)
            adEventDecoration.onError?.invoke(it)
        },
        {
            onDestroy?.invoke()
            adEventDecoration.onDestroy?.invoke()
        },
        {
            onStartLoad?.invoke()
            adEventDecoration.onStartLoad?.invoke()
        },
        {
            onTimeout?.invoke()
            adEventDecoration.onTimeout?.invoke()
        },
    )
}

internal fun BannerAdapterDelegate.decorate(adEventDecoration: AdEventDecoration): BannerAdapterDelegate =
    with(adEventDecoration) {
        DecoratedBannerAdapterDelegate(
            onLoad,
            onShow,
            onImpression,
            onClick?.let { { it() } },
            onError,
            onDestroy,
            onStartLoad,
            onTimeout,
            this@decorate
        )
    }

internal fun InterstitialAdapterDelegate.decorate(adEventDecoration: AdEventDecoration): InterstitialAdapterDelegate =
    with(adEventDecoration) {
        DecoratedInterstitialAdapterDelegate(
            onLoad,
            onShow,
            onImpression,
            onSkip,
            onComplete,
            onHide,
            onClick,
            onError,
            onDestroy,
            onStartLoad,
            onTimeout,
            this@decorate,
        )
    }

internal fun RewardedInterstitialAdapterDelegate.decorate(adEventDecoration: AdEventDecoration): RewardedInterstitialAdapterDelegate =
    with(adEventDecoration) {
        DecoratedRewardedInterstitialAdapterDelegate(
            onLoad,
            onShow,
            onImpression,
            onReward,
            onHide,
            onClick,
            onError,
            onDestroy,
            onStartLoad,
            onTimeout,
            this@decorate
        )
    }

fun baseAdDecoration() = AdEventDecoration()

internal fun bidAdDecoration(
    bidId: String,
    auctionId: String,
    eventTracker: EventTracker,
) = AdEventDecoration(
    onLoad = {},
    onImpression = {
        val scope = GlobalScopes.IO
        scope.launch {
            TrackingFieldResolver.saveLoadedBid(auctionId, bidId)
            var payload = TrackingFieldResolver.buildPayload(auctionId)
            payload = payload?.replace(auctionId, auctionId)
            val accountId = TrackingFieldResolver.getAccountId()

            if (payload != null && accountId != null) {
                val secret = XorEncryption.generateXorSecret(accountId)
                val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
                val impressionId = XorEncryption.encrypt(payload, secret)
                eventTracker.send(impressionId, campaignId, "1", EventType.IMPRESSION)
            }
        }
    },
    onClick = {
        val scope = GlobalScopes.IO
        scope.launch {
            TrackingFieldResolver.saveLoadedBid(auctionId, bidId)
            val clickCount = ClickCounterTracker.incrementAndGet(auctionId)

            val payload = TrackingFieldResolver.buildPayload(auctionId)?.replace(
                auctionId,
                "$auctionId-$clickCount"
            )
            val accountId = TrackingFieldResolver.getAccountId()

            if (payload != null && accountId != null) {
                val secret = XorEncryption.generateXorSecret(accountId)
                val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
                val impressionId = XorEncryption.encrypt(payload, secret)
                eventTracker.send(impressionId, campaignId, "1", EventType.CLICK)
            }
        }
    }
)

internal fun adapterLoggingDecoration(
    placementId: String,
    adNetwork: AdNetwork,
    networkTimeoutMillis: Long,
    type: AdType,
    placementName: String,
    price: Double,
): AdEventDecoration {
    val tag = "${adNetwork}${type}Adapter"

    return AdEventDecoration(
        onTimeout = {
            CloudXLogger.d(
                tag,
                "LOAD TIMEOUT placement: $placementName, id: $placementId, price: $price"
            )
        },
        onLoad = {
            CloudXLogger.d(
                tag,
                "LOAD SUCCESS placement: $placementName, id: $placementId, price: $price"
            )
        },
        onError = {
            CloudXLogger.e(
                tag,
                "ERROR placement: $placementName, id: $placementId, price: $price, error: ${it.description}"
            )
        },
        onImpression = {
            CloudXLogger.d(
                tag,
                "IMPRESSION placement: $placementName, id: $placementId, price: $price"
            )
        },
    )
}