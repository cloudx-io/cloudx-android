package io.cloudx.sdk.internal.ads

import com.xor.XorEncryption
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegate
import io.cloudx.sdk.internal.ads.banner.DecoratedBannerAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.DecoratedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.interstitial.InterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.DecoratedRewardedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.ads.fullscreen.rewarded.RewardedInterstitialAdapterDelegate
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.tracker.ClickCounterTracker
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.launch

private typealias Func = (() -> Unit)
private typealias ClickFunc = (() -> Unit)
private typealias ErrorFunc = ((error: CloudXError) -> Unit)

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
            onLoad = onLoad,
            onShow = onShow,
            onImpression = onImpression,
            onReward = onReward,
            onHide = onHide,
            onClick = onClick,
            onError = onError,
            onDestroy = onDestroy,
            onStartLoad = onStartLoad,
            onTimeout = onTimeout,
            rewardedInterstitial = this@decorate
        )
    }

fun baseAdDecoration() = AdEventDecoration()

internal fun bidAdDecoration(
    bid: Bid,
    auctionId: String,
    eventTracker: EventTracker,
    winLossTracker: WinLossTracker,
) = AdEventDecoration(
    onLoad = {},
    onImpression = {
        ThreadUtils.GlobalIOScope.launch {
            // Save the loaded bid for existing impression tracking
            TrackingFieldResolver.saveLoadedBid(auctionId, bid.id)
            var payload = TrackingFieldResolver.buildPayload(auctionId)
            payload = payload?.replace(auctionId, auctionId)
            val accountId = TrackingFieldResolver.getAccountId()

            if (payload != null && accountId != null) {
                val secret = XorEncryption.generateXorSecret(accountId)
                val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
                val impressionId = XorEncryption.encrypt(payload, secret)
                eventTracker.send(impressionId, campaignId, "1", EventType.IMPRESSION)
            }

            winLossTracker.sendWin(auctionId, bid)
        }
    },
    onClick = {
        ThreadUtils.GlobalIOScope.launch {
            TrackingFieldResolver.saveLoadedBid(auctionId, bid.id)
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
            CXLogger.d(
                tag,
                "LOAD TIMEOUT placement: $placementName, id: $placementId, price: $price"
            )
        },
        onLoad = {
            CXLogger.d(
                tag,
                "LOAD SUCCESS placement: $placementName, id: $placementId, price: $price"
            )
        },
        onError = {
            CXLogger.e(
                tag,
                "ERROR placement: $placementName, id: $placementId, price: $price, error: ${it.effectiveMessage}"
            )
        },
        onImpression = {
            CXLogger.d(
                tag,
                "IMPRESSION placement: $placementName, id: $placementId, price: $price"
            )
        },
    )
}