package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.win_loss.LossReason
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.cloudx.sdk.toFailure
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
    private val winLossTracker: WinLossTracker
) {
    private val TAG = "AdLoader"

    /**
     * Returns a loaded ad or null (no fill / failure)
     */
    suspend fun load(): Result<T, CloudXError> {
        return when (val result = bidAdSource.requestBid()) {
            is Result.Success -> {
                val banner = loadWinner(result.value)
                banner?.toSuccess()
                    ?: CloudXErrorCode.NO_FILL.toFailure(message = "No fill - all candidates failed to load")
            }

            is Result.Failure -> {
                Result.Failure(result.value)
            }
        }
    }

    private suspend fun loadWinner(
        bidResponse: BidAdSourceResponse<T>
    ): T? = coroutineScope {

        var loadedAd: T? = null
        var loadedAdIndex = -1
        var winnerBidPrice = -1f

        for ((index, bidItem) in bidResponse.bidItemsByRank.withIndex()) {
            ensureActive()

            val ad = createAndLoadAdapter(bidAdLoadTimeoutMillis, bidItem.createBidAd)

            if (ad != null) {
                CXLogger.i(
                    TAG,
                    placementName,
                    "Loaded: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})"
                )

                loadedAd = ad
                loadedAdIndex = index
                winnerBidPrice = bidItem.bid.price ?: -1f
                break
            } else {
                CXLogger.w(
                    TAG,
                    placementName,
                    "Failed: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})"
                )

                winLossTracker.sendLoss(
                    bidResponse.auctionId,
                    bidItem.bid,
                    LossReason.INTERNAL_ERROR,
                    winnerBidPrice
                )
            }
        }

        if (loadedAdIndex != -1) {
            bidResponse.bidItemsByRank.forEachIndexed { index, bidItem ->
                if (index > loadedAdIndex) {
                    winLossTracker.sendLoss(
                        bidResponse.auctionId,
                        bidItem.bid,
                        LossReason.LOST_TO_HIGHER_BID,
                        winnerBidPrice
                    )
                }
            }
        }

        loadedAd
    }

    private suspend fun createAndLoadAdapter(
        timeoutMs: Long,
        createAdapter: suspend () -> T
    ): T? {
        val ad = try {
            createAdapter()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CXLogger.e(TAG, placementName, "Failed to create ad", e)
            return null
        }

        var loaded = false
        return try {
            CXLogger.d(TAG, placementName, "Loading ad (timeout=${timeoutMs}ms)")
            connectionStatusService.awaitConnection()
            loaded = withTimeout(timeoutMs) {
                ad.load()
            }
            if (loaded) ad else null
        } catch (e: TimeoutCancellationException) {
            CXLogger.w(TAG, placementName, "Load timeout ${timeoutMs}ms", e)
            ad.timeout()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CXLogger.e(TAG, placementName, "Load failed", e)
            null
        } finally {
            if (!loaded) ad.destroy()
        }
    }
}