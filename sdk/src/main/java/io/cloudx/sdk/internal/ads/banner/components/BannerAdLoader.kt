package io.cloudx.sdk.internal.ads.banner.components

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.BidAdSource
import io.cloudx.sdk.internal.ads.BidAdSourceResponse
import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegate
import io.cloudx.sdk.internal.bid.LoadResult
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.bid.LossReporter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal class BannerAdLoader(
    private val bidAdSource: BidAdSource<BannerAdapterDelegate>,
    private val bidAdLoadTimeoutMillis: Long,
    private val placementName: String,
    private val placementId: String
) {
    private val TAG = "BannerAdLoader"

    /** Single non-retrying attempt per MVP. Returns a loaded banner or null (no fill / failure). */
    suspend fun loadOnce(): Result<BannerAdapterDelegate?, CLXError> {
        return when (val result = bidAdSource.requestBid()) {
            is Result.Success -> {
                val banner = loadWinnerFrom(result.value)
                if (banner != null) {
                    Result.Success(banner)
                } else {
                    Result.Failure(CLXError(CLXErrorCode.NO_FILL, "No fill - all candidates failed to load"))
                }
            }
            is Result.Failure -> {
                Result.Failure(result.value)
            }
        }
    }

    private suspend fun loadWinnerFrom(
        bids: BidAdSourceResponse<BannerAdapterDelegate>
    ): BannerAdapterDelegate? = coroutineScope {
        var winner: BannerAdapterDelegate? = null
        var winnerIndex = -1
        val lossReasons = mutableMapOf<String, LossReason>()

        for ((index, bidItem) in bids.bidItemsByRank.withIndex()) {
            ensureActive()

            val result = loadSingleBanner(bidAdLoadTimeoutMillis, bidItem.createBidAd)
            val banner = result.banner

            if (banner != null) {
                CloudXLogger.i(
                    TAG, placementName, placementId,
                    "Loaded: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.rank})"
                )
                winner = banner
                winnerIndex = index
                break
            } else {
                CloudXLogger.w(
                    TAG, placementName, placementId,
                    "Failed: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.rank})"
                )
                lossReasons[bidItem.id] = result.lossReason ?: LossReason.TechnicalError
            }
        }

        // fire LURLs for non-winners
        if (winnerIndex != -1) {
            bids.bidItemsByRank.forEachIndexed { idx, item ->
                if (idx != winnerIndex && !lossReasons.containsKey(item.id)) {
                    lossReasons[item.id] = LossReason.LostToHigherBid
                }
            }
            bids.bidItemsByRank.forEachIndexed { idx, item ->
                if (idx != winnerIndex) {
                    val reason = lossReasons[item.id] ?: LossReason.LostToHigherBid
                    item.lurl?.takeIf { it.isNotBlank() }?.let { LossReporter.fireLoss(it, reason) }
                }
            }
        }

        winner
    }

    private suspend fun loadSingleBanner(
        timeoutMs: Long,
        createBanner: suspend () -> BannerAdapterDelegate
    ): LoadResult {
        val banner = try {
            createBanner()
        } catch (e: Exception) {
            return LoadResult(null, LossReason.TechnicalError)
        }

        var loaded = false
        return try {
            CloudXLogger.d(
                TAG,
                placementName,
                placementId,
                "Loading banner (timeout=${timeoutMs}ms)"
            )
            loaded = withTimeout(timeoutMs) { banner.load() }
            if (loaded) LoadResult(banner, null) else LoadResult(null, LossReason.TechnicalError)
        } catch (e: TimeoutCancellationException) {
            CloudXLogger.w(TAG, placementName, placementId, "Load timeout ${timeoutMs}ms")
            banner.timeout()
            LoadResult(null, LossReason.TechnicalError)
        } catch (e: Exception) {
            CloudXLogger.e(TAG, placementName, placementId, "Load failed", e)
            LoadResult(null, LossReason.TechnicalError)
        } finally {
            if (!loaded) banner.destroy()
        }
    }
}

