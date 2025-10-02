package io.cloudx.sdk.internal.ads

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
import io.cloudx.sdk.internal.tracker.XorEncryption
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.launch

private typealias Func = (() -> Unit)
private typealias ClickFunc = (() -> Unit)
private typealias ErrorFunc = ((error: CloudXError) -> Unit)

class AdEventDecorator(
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

    operator fun plus(adEventDecorator: AdEventDecorator) = AdEventDecorator(
        {
            onLoad?.invoke()
            adEventDecorator.onLoad?.invoke()
        },
        {
            onShow?.invoke()
            adEventDecorator.onShow?.invoke()
        },
        {
            onHide?.invoke()
            adEventDecorator.onHide?.invoke()
        },
        {
            onImpression?.invoke()
            adEventDecorator.onImpression?.invoke()
        },
        {
            onSkip?.invoke()
            adEventDecorator.onSkip?.invoke()
        },
        {
            onComplete?.invoke()
            adEventDecorator.onComplete?.invoke()
        },
        {
            onReward?.invoke()
            adEventDecorator.onReward?.invoke()
        },
        {
            onClick?.invoke()
            adEventDecorator.onClick?.invoke()
        },
        {
            onError?.invoke(it)
            adEventDecorator.onError?.invoke(it)
        },
        {
            onDestroy?.invoke()
            adEventDecorator.onDestroy?.invoke()
        },
        {
            onStartLoad?.invoke()
            adEventDecorator.onStartLoad?.invoke()
        },
        {
            onTimeout?.invoke()
            adEventDecorator.onTimeout?.invoke()
        },
    )
}

internal fun BannerAdapterDelegate.decorate(adEventDecorator: AdEventDecorator): BannerAdapterDelegate =
    with(adEventDecorator) {
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

internal fun InterstitialAdapterDelegate.decorate(adEventDecorator: AdEventDecorator): InterstitialAdapterDelegate =
    with(adEventDecorator) {
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

internal fun RewardedInterstitialAdapterDelegate.decorate(adEventDecorator: AdEventDecorator): RewardedInterstitialAdapterDelegate =
    with(adEventDecorator) {
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

internal fun createAdEventTrackingDecorator(
    bid: Bid,
    auctionId: String,
    eventTracker: EventTracker,
    winLossTracker: WinLossTracker,
) = AdEventDecorator(
    onLoad = {},
    onImpression = {
        ThreadUtils.GlobalIOScope.launch {
            var payload = TrackingFieldResolver.buildPayload(auctionId, bid.id)
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
            val clickCount = ClickCounterTracker.incrementAndGet(auctionId)

            val payload = TrackingFieldResolver.buildPayload(auctionId, bid.id)?.replace(
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

internal fun createAdapterEventLoggingDecorator(
    placementName: String,
    adNetwork: AdNetwork,
    type: AdType,
): AdEventDecorator {
    val tag = "${adNetwork}${type}Adapter"
    return AdEventDecorator(
        onStartLoad = {
            CXLogger.d(tag, placementName, "STARTING LOAD")
        },
        onLoad = {
            CXLogger.d(tag, placementName, "LOAD SUCCESS")
        },
        onShow = {
            CXLogger.d(tag, placementName, "SHOW")
        },
        onImpression = {
            CXLogger.d(tag, placementName, "IMPRESSION")
        },
        onClick = {
            CXLogger.d(tag, placementName, "CLICK")
        },
        onHide = {
            CXLogger.d(tag, placementName, "HIDE")
        },
        onSkip = {
            CXLogger.d(tag, placementName, "SKIP")
        },
        onComplete = {
            CXLogger.d(tag, placementName, "COMPLETE")
        },
        onReward = {
            CXLogger.d(tag, placementName, "REWARD")
        },
        onTimeout = {
            CXLogger.w(tag, placementName, "LOAD TIMEOUT")
        },
        onError = { error ->
            CXLogger.e(tag, placementName, "ERROR - ${error.effectiveMessage}")
        },
        onDestroy = {
            CXLogger.d(tag, placementName, "DESTROY")
        }
    )
}