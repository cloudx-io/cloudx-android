package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal class AdLoader<T : CXAdapterDelegate>(
    private val placementName: String,
    private val placementId: String,
    private val bidAdSource: BidAdSource<T>,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val winLossTracker: io.cloudx.sdk.internal.imp_tracker.win_loss.WinLossTracker
) {
    private val TAG = "AdLoader"

    /**
     * Returns a loaded ad or null (no fill / failure)
     */
    suspend fun load(): Result<T, CloudXError> {
        return when (val result = bidAdSource.requestBid()) {
            is Result.Success -> {
                val banner = loadWinner(result.value)
                if (banner != null) {
                    Result.Success(banner)
                } else {
                    Result.Failure(
                        CloudXError(
                            CloudXErrorCode.NO_FILL,
                            "No fill - all candidates failed to load"
                        )
                    )
                }
            }

            is Result.Failure -> {
                Result.Failure(result.value)
            }
        }
    }

    private suspend fun loadWinner(
        bids: BidAdSourceResponse<T>
    ): T? = coroutineScope {
        var loadedAd: T? = null
        var loadedAdIndex = -1
        val lossReasons = mutableMapOf<String, LossReason>()

        for ((index, bidItem) in bids.bidItemsByRank.withIndex()) {
            ensureActive()

            val ad = loadAd(bidAdLoadTimeoutMillis, bidItem.createBidAd)

            if (ad != null) {
                CXLogger.i(TAG, placementName, placementId, "Loaded: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})")

                loadedAd = ad
                loadedAdIndex = index
                winLossTracker.setBidLoadResult(bids.auctionId, bidItem.bid.id, true)
                break
            } else {
                CXLogger.w(TAG, placementName, placementId, "Failed: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})")

                lossReasons[bidItem.bid.id] = LossReason.TechnicalError
                winLossTracker.setBidLoadResult(bids.auctionId, bidItem.bid.id, false, LossReason.TechnicalError)
                winLossTracker.sendLoss(bids.auctionId, bidItem.bid.id)
            }
        }

        if (loadedAdIndex != -1) {
            bids.bidItemsByRank.forEachIndexed { index, bidItem ->
                if (index != loadedAdIndex && !lossReasons.containsKey(bidItem.bid.id)) {
                    lossReasons[bidItem.bid.id] = LossReason.LostToHigherBid
                    winLossTracker.setBidLoadResult(bids.auctionId, bidItem.bid.id, false, LossReason.LostToHigherBid)
                    winLossTracker.sendLoss(bids.auctionId, bidItem.bid.id)
                }
            }
        }

        if (loadedAd == null) {
            CXLogger.d(TAG, "No loaded ad found for auction ${bids.auctionId}, clearing auction data")
            winLossTracker.clearAuction(bids.auctionId)
        }

        loadedAd
    }

    private suspend fun loadAd(
        timeoutMs: Long,
        createAd: suspend () -> T
    ): T? {
        val ad = try {
            createAd()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }

        var loaded = false
        return try {
            CXLogger.d(
                TAG,
                placementName,
                placementId,
                "Loading ad (timeout=${timeoutMs}ms)"
            )
            connectionStatusService.awaitConnection()
            loaded = withTimeout(timeoutMs) {
                ad.load()
            }
            if (loaded) ad else null
        } catch (e: TimeoutCancellationException) {
            CXLogger.w(TAG, placementName, placementId, "Load timeout ${timeoutMs}ms", e)
            ad.timeout()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CXLogger.e(TAG, placementName, placementId, "Load failed", e)
            null
        } finally {
            if (!loaded) ad.destroy()
        }
    }
}