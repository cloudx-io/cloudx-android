package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver

internal class WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null

    companion object {
        private const val PLACEHOLDER_AUCTION_PRICE = "\${AUCTION_PRICE}"
        private const val PLACEHOLDER_AUCTION_LOSS = "\${AUCTION_LOSS}"
    }

    fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossPayloadMapping = payloadMapping
    }

    fun buildWinLossPayload(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason,
        bidLifecycleEvent: BidLifecycleEvent,
        loadedBidPrice: Float
    ): Map<String, Any>? {
        val payloadMapping = winLossPayloadMapping ?: return null
        val result = mutableMapOf<String, Any>()

        payloadMapping.forEach { (payloadKey, fieldPath) ->
            val resolvedValue = resolveWinLossField(auctionId, bid, lossReason, fieldPath, bidLifecycleEvent, loadedBidPrice)
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
        fieldPath: String,
        bidLifecycleEvent: BidLifecycleEvent,
        loadedBidPrice: Float
    ): Any? {
        return when (fieldPath) {
            "sdk.[renderSuccess|loss|loadStart|loadSuccess]" -> {}
            "sdk.lossReason" -> lossReason.description
            "sdk.sdk" -> "sdk"
            "sdk.[bid.nurl|bid.lurl]" -> {
                val url = if (true) {
                    bid?.rawJson?.optString("nurl")
                } else {
                    bid?.rawJson?.optString("lurl")
                }

                url?.let { replaceUrlTemplates(it, bidLifecycleEvent, lossReason, loadedBidPrice) }
            }

            "sdk.loopIndex" -> {
                val loopIndex = TrackingFieldResolver.resolveField(auctionId, fieldPath) as? String
                loopIndex?.toIntOrNull()
            }

            else -> {
                TrackingFieldResolver.resolveField(auctionId, fieldPath, bid?.id)
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
        bidLifecycleEvent: BidLifecycleEvent,
        lossReason: LossReason,
        loadedBidPrice: Float
    ): String {
        var processedUrl = url

        if (processedUrl.contains(PLACEHOLDER_AUCTION_PRICE)) {
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_PRICE, loadedBidPrice.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_LOSS)) {
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_LOSS, lossReason.code.toString())
        }

        return processedUrl
    }
}