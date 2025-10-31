package io.cloudx.sdk.internal.tracker.win_loss

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver

internal class WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null

    fun setPayloadMapping(payloadMapping: Map<String, String>) {
        winLossPayloadMapping = payloadMapping
    }

    fun buildWinLossPayload(
        auctionId: String,
        bid: Bid?,
        lossReason: LossReason,
        bidLifecycleEvent: BidLifecycleEvent?,
        error: CloudXError? = null
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
                error
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
        error: CloudXError?
    ): Any? {
        return when {
            fieldPath == "sdk.sdk" -> "sdk"
            fieldPath == "sdk.loopIndex" -> {
                val loopIndex = TrackingFieldResolver.resolveField(auctionId, fieldPath) as? String
                loopIndex?.toIntOrNull()
            }
            payloadKey == "notificationType" -> bidLifecycleEvent?.notificationType
            payloadKey == "bid" -> {
                bid?.rawJson
            }
            payloadKey == "error" -> {
                error?.let { serializeError(it) }
            }
            payloadKey == "lossReasonCode" -> {
                lossReason.code
            }
            else -> TrackingFieldResolver.resolveField(auctionId, fieldPath, bid?.id)
        }
    }

    private fun serializeError(error: CloudXError): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("code", error.code.name)
            put("message", error.effectiveMessage)
        }
    }
}