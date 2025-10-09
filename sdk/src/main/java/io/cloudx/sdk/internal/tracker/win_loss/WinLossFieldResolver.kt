package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver

internal class WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null

    companion object {
        private const val PLACEHOLDER_AUCTION_PRICE = "\${AUCTION_PRICE}"
        private const val PLACEHOLDER_AUCTION_LOSS = "\${AUCTION_LOSS}"
        private const val PLACEHOLDER_AUCTION_MIN_TO_WIN = "\${AUCTION_MIN_TO_WIN}"
    }

    fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossPayloadMapping = payloadMapping
    }

    fun buildWinLossPayload(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason,
        bidLifecycleEvent: BidLifecycleEvent?,
        loadedBidPrice: Float,
        auctionMinToWin: Float = -1f
    ): Map<String, Any>? {
        val payloadMapping = winLossPayloadMapping ?: return null
        val result = mutableMapOf<String, Any>()

        payloadMapping.forEach { (payloadKey, fieldPath) ->
            val resolvedValue = resolveWinLossField(
                auctionId,
                bid,
                lossReason,
                payloadKey,
                fieldPath,
                bidLifecycleEvent,
                loadedBidPrice,
                auctionMinToWin
            )
            if (resolvedValue != null) {
                result[payloadKey] = resolvedValue
            }
        }
        return result
    }

    private fun resolveWinLossField(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason,
        payloadKey: String,
        fieldPath: String,
        bidLifecycleEvent: BidLifecycleEvent?,
        loadedBidPrice: Float,
        auctionMinToWin: Float
    ): Any? {
        return when {
            fieldPath == "sdk.lossReason" -> lossReason.description
            fieldPath == "sdk.sdk" -> "sdk"
            fieldPath == "sdk.loopIndex" -> {
                val loopIndex = TrackingFieldResolver.resolveField(auctionId, fieldPath) as? String
                loopIndex?.toIntOrNull()
            }
            payloadKey == "notificationType" -> bidLifecycleEvent?.notificationType
            payloadKey == "url" -> {
                val url = bid?.rawJson?.optString(bidLifecycleEvent?.urlType)
                url?.let { replaceUrlTemplates(it, lossReason, loadedBidPrice, auctionMinToWin) }
            }
            else -> TrackingFieldResolver.resolveField(auctionId, fieldPath, bid?.id)
        }
    }

    /**
     * Replace URL templates with actual values for win/loss notifications
     *
     * Supported templates:
     * - ${AUCTION_PRICE} -> actual winning bid price or losing bid price
     * - ${AUCTION_LOSS} -> loss reason code
     * - ${AUCTION_MIN_TO_WIN} -> minimum price to win the auction
     */
    private fun replaceUrlTemplates(
        url: String,
        lossReason: LossReason,
        loadedBidPrice: Float,
        auctionMinToWin: Float
    ): String {
        var processedUrl = url

        if (processedUrl.contains(PLACEHOLDER_AUCTION_PRICE)) {
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_PRICE, loadedBidPrice.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_LOSS)) {
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_LOSS, lossReason.code.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_MIN_TO_WIN)) {
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_MIN_TO_WIN, auctionMinToWin.toString())
        }

        return processedUrl
    }
}