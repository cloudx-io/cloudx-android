package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver

internal class WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null

    companion object {
        private const val PLACEHOLDER_AUCTION_PRICE = "\${AUCTION_PRICE}"
        private const val PLACEHOLDER_AUCTION_LOSS = "\${AUCTION_LOSS}"
    }

    fun setConfig(config: Config) {
        winLossPayloadMapping = config.winLossNotificationPayloadConfig
    }

    fun buildWinLossPayload(auctionId: String, bidId: String, isWin: Boolean): Map<String, Any>? {
        val payloadMapping = winLossPayloadMapping ?: return null
        val result = mutableMapOf<String, Any>()

        payloadMapping.forEach { (payloadKey, fieldPath) ->
            val resolvedValue = resolveWinLossField(auctionId, bidId, fieldPath, isWin)
            if (resolvedValue != null) {
                result[payloadKey] = resolvedValue
            }
        }
        return result
    }

    private fun resolveWinLossField(
        auctionId: String,
        bidId: String,
        fieldPath: String,
        isWin: Boolean
    ): Any? {
        val currentBid = AuctionBidManager.getBid(auctionId, bidId)
        return when (fieldPath) {
            "sdk.win" -> if (isWin) "win" else null
            "sdk.loss" -> if (!isWin) "loss" else null
            "sdk.lossReason" -> AuctionBidManager.getBidLossReason(auctionId, bidId)
            "sdk.[win|loss]" -> if (isWin) "win" else "loss"
            "sdk.sdk" -> "sdk"
            "sdk.[bid.nurl|bid.lurl]" -> {
                val url = if (isWin) {
                    currentBid?.rawJson?.optString("nurl")
                } else {
                    currentBid?.rawJson?.optString("lurl")
                }

                url?.let { replaceUrlTemplates(it, isWin, currentBid, auctionId, bidId) }
            }

            "sdk.loopIndex" -> {
                val loopIndex = TrackingFieldResolver.resolveField(auctionId, fieldPath) as? String
                loopIndex?.toIntOrNull()
            }

            else -> {
                TrackingFieldResolver.resolveField(auctionId, fieldPath)
            }
        }
    }

    /**
     * Replace URL templates with actual values for win/loss notifications
     *
     * Supported templates:
     * - ${AUCTION_PRICE} -> actual winning bid price or losing bid price
     * - ${AUCTION_LOSS} -> loss reason code (1, 4)
     */
    private fun replaceUrlTemplates(
        url: String,
        isWin: Boolean,
        currentBid: Bid?,
        auctionId: String,
        bidId: String
    ): String {
        var processedUrl = url

        if (processedUrl.contains(PLACEHOLDER_AUCTION_PRICE)) {
            val price = if (isWin) {
                // For win notifications, use winning bid price
                AuctionBidManager.getWinningBid(auctionId)?.price?.toDouble() ?: 0.0
            } else {
                // For loss notifications, use the losing bid's price
                currentBid?.price?.toDouble() ?: 0.0
            }
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_PRICE, price.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_LOSS) && !isWin) {
            val lossReasonCode = AuctionBidManager.getBidLossReason(auctionId, bidId)?.code ?: 1
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_LOSS, lossReasonCode.toString())
        }

        return processedUrl
    }
}