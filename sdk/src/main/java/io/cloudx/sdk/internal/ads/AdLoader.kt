package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.internal.CLXError
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.bid.LossTracker
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal class AdLoader<T : CXAdapterDelegate>(
    private val placementName: String,
    private val placementId: String,
    private val bidAdSource: BidAdSource<T>,
    private val bidAdLoadTimeoutMillis: Long,
    private val connectionStatusService: ConnectionStatusService
) {
    private val TAG = "AdLoader"

    /**
     * Returns a loaded ad or null (no fill / failure)
     */
    suspend fun load(): Result<T, CLXError> {
        return when (val result = bidAdSource.requestBid()) {
            is Result.Success -> {
                val banner = loadWinner(result.value)
                if (banner != null) {
                    Result.Success(banner)
                } else {
                    Result.Failure(
                        CLXError(
                            CLXErrorCode.NO_FILL,
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
        var winner: T? = null
        var winnerIndex = -1
        val lossReasons = mutableMapOf<String, LossReason>()

        for ((index, bidItem) in bids.bidItemsByRank.withIndex()) {
            ensureActive()

            val ad = loadAd(bidAdLoadTimeoutMillis, bidItem.createBidAd)

            if (ad != null) {
                CXLogger.i(
                    TAG, placementName, placementId,
                    "Loaded: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.rank})"
                )
                winner = ad
                winnerIndex = index
                break
            } else {
                CXLogger.w(
                    TAG, placementName, placementId,
                    "Failed: ${bidItem.adNetworkOriginal.networkName} (rank=${bidItem.rank})"
                )
                lossReasons[bidItem.id] = LossReason.TechnicalError
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
                    item.lurl?.takeIf { it.isNotBlank() }?.let { LossTracker.trackLoss(it, reason) }
                }
            }
        }

        winner
    }

    private suspend fun loadAd(
        timeoutMs: Long,
        createAd: suspend () -> T
    ): T? {
        val ad = try {
            createAd()
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
        } catch (e: Exception) {
            CXLogger.e(TAG, placementName, placementId, "Load failed", e)
            null
        } finally {
            if (!loaded) ad.destroy()
        }
    }
}