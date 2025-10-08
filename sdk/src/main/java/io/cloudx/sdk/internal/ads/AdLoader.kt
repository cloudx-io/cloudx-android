package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.tracker.win_loss.BidLifecycleEvent
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
    placementName: String,
    private val placementId: String,
    private val bidAdSource: BidAdSource<T>,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService,
    private val winLossTracker: WinLossTracker
) {
    private val logger = CXLogger.forPlacement("AdLoader", placementName)

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
                logger.i("Loaded: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})")

                loadedAd = ad
                loadedAdIndex = index
                winnerBidPrice = bidItem.bid.price ?: -1f

                winLossTracker.sendEvent(
                    bidResponse.auctionId,
                    bidItem.bid,
                    BidLifecycleEvent.LOAD_SUCCESS,
                    LossReason.BID_WON
                )
                break
            } else {
                logger.w("Failed: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.bid.rank})")

                winLossTracker.sendEvent(
                    bidResponse.auctionId,
                    bidItem.bid,
                    BidLifecycleEvent.LOSS,
                    LossReason.INTERNAL_ERROR
                )
            }
        }

        if (loadedAdIndex != -1) {
            bidResponse.bidItemsByRank.forEachIndexed { index, bidItem ->
                if (index > loadedAdIndex) {
                    winLossTracker.sendEvent(
                        bidResponse.auctionId,
                        bidItem.bid,
                        BidLifecycleEvent.LOSS,
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
            logger.e("Failed to create ad", e)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Ad adapter creation failed: ${e.message}",
                errorDetails = e.stackTraceToString()
            )
            return null
        }

        var loaded = false
        return try {
            logger.d("Loading ad (timeout=${timeoutMs}ms)")
            connectionStatusService.awaitConnection()
            loaded = withTimeout(timeoutMs) {
                ad.load()
            }
            if (loaded) ad else null
        } catch (e: TimeoutCancellationException) {
            logger.w("Load timeout ${timeoutMs}ms", e)
            ad.timeout()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Load failed", e)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Ad loading failed: ${e.message}",
                errorDetails = e.stackTraceToString()
            )
            null
        } finally {
            if (!loaded) ad.destroy()
        }
    }
}