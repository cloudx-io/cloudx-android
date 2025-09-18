package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.bid.LossReason
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

    fun buildWinLossPayload(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason?,
        isWin: Boolean
    ): Map<String, Any>? {
        val payloadMapping = winLossPayloadMapping ?: return null
        val result = mutableMapOf<String, Any>()

        payloadMapping.forEach { (payloadKey, fieldPath) ->
            val resolvedValue = resolveWinLossField(auctionId, bid, lossReason, fieldPath, isWin)
            if (resolvedValue != null) {
                result[payloadKey] = resolvedValue
            }
        }
        return result
    }

    private fun resolveWinLossField(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason?,
        fieldPath: String,
        isWin: Boolean
    ): Any? {
        return when (fieldPath) {
            "sdk.win" -> if (isWin) "win" else null
            "sdk.loss" -> if (!isWin) "loss" else null
            "sdk.lossReason" -> lossReason?.code
            "sdk.[win|loss]" -> if (isWin) "win" else "loss"
            "sdk.sdk" -> "sdk"
            "sdk.[bid.nurl|bid.lurl]" -> {
                val url = if (isWin) {
                    bid?.rawJson?.optString("nurl")
                } else {
                    bid?.rawJson?.optString("lurl")
                }

                url?.let { replaceUrlTemplates(it, isWin, bid, lossReason) }
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
        lossReason: LossReason?,
    ): String {
        var processedUrl = url

        if (processedUrl.contains(PLACEHOLDER_AUCTION_PRICE)) {
            // Use the passed bid data for both win and loss cases
            val price = currentBid?.price?.toDouble() ?: 0.0
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_PRICE, price.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_LOSS) && !isWin) {
            val lossReasonCode = lossReason?.code ?: 1
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_LOSS, lossReasonCode.toString())
        }

        return processedUrl
    }
}