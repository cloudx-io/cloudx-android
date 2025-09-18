package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver
import org.json.JSONObject

internal class WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null
    private val winLossDataMap = mutableMapOf<String, WinLossData>()

    companion object {
        private const val PLACEHOLDER_AUCTION_PRICE = "\${AUCTION_PRICE}"
        private const val PLACEHOLDER_AUCTION_LOSS = "\${AUCTION_LOSS}"
    }

    data class WinLossData(
        val isWin: Boolean,
        val lossReason: LossReason? = null,
        val winPrice: Double? = null,
        val additionalData: Map<String, Any> = emptyMap()
    )

    fun setConfig(config: Config) {
        winLossPayloadMapping = config.winLossNotificationPayloadConfig
    }

    fun setWinData(
        auctionId: String,
        winPrice: Double,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        winLossDataMap[auctionId] = WinLossData(
            isWin = true,
            winPrice = winPrice,
            additionalData = additionalData
        )
    }

    fun setLossData(
        auctionId: String,
        lossReason: LossReason,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        winLossDataMap[auctionId] = WinLossData(
            isWin = false,
            lossReason = lossReason,
            additionalData = additionalData
        )
    }

    fun buildWinLossPayload(auctionId: String): Map<String, Any>? {
        val payloadMapping = winLossPayloadMapping ?: return null
        val winLossData = winLossDataMap[auctionId] ?: return null
        val result = mutableMapOf<String, Any>()

        payloadMapping.forEach { (payloadKey, fieldPath) ->
            val resolvedValue = resolveWinLossField(auctionId, fieldPath, winLossData)
            if (resolvedValue != null) {
                result[payloadKey] = resolvedValue
            }
        }
        return result
    }

    private fun resolveWinLossField(
        auctionId: String,
        fieldPath: String,
        winLossData: WinLossData
    ): Any? {
        return when (fieldPath) {
            "sdk.win" -> if (winLossData.isWin) "win" else null
            "sdk.loss" -> if (!winLossData.isWin) "loss" else null
            "sdk.lossReason" -> winLossData.lossReason?.code
            "sdk.[win|loss]" -> if (winLossData.isWin) "win" else "loss"
            "sdk.sdk" -> "sdk"
            "sdk.[bid.nurl|bid.lurl]" -> {
                val currentBidData = winLossData.additionalData["rawBid"] as? JSONObject
                val url = if (winLossData.isWin) {
                    currentBidData?.optString("nurl") ?: currentBidData?.optString("burl")
                } else {
                    currentBidData?.optString("lurl")
                }

                url?.let { replaceUrlTemplates(it, winLossData) }
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

    fun clear() {
        winLossDataMap.clear()
    }

    fun clearAuction(auctionId: String) {
        winLossDataMap.remove(auctionId)
    }

    /**
     * Replace URL templates with actual values for win/loss notifications
     *
     * Supported templates:
     * - ${AUCTION_PRICE} -> actual winning bid price or losing bid price
     * - ${AUCTION_LOSS} -> loss reason code (1, 4)
     */
    private fun replaceUrlTemplates(url: String, winLossData: WinLossData): String {
        var processedUrl = url

        if (processedUrl.contains(PLACEHOLDER_AUCTION_PRICE)) {
            val price = if (winLossData.isWin) {
                winLossData.winPrice
            } else {
                (winLossData.additionalData["bidPrice"] as? Number)?.toDouble() ?: 0.0
            }
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_PRICE, price.toString())
        }

        if (processedUrl.contains(PLACEHOLDER_AUCTION_LOSS) && !winLossData.isWin) {
            val lossReasonCode = winLossData.lossReason?.code ?: 1
            processedUrl = processedUrl.replace(PLACEHOLDER_AUCTION_LOSS, lossReasonCode.toString())
        }

        return processedUrl
    }
}