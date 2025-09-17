package io.cloudx.sdk.internal.imp_tracker.win_loss

import io.cloudx.sdk.internal.bid.LossReason
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.imp_tracker.TrackingFieldResolver

internal object WinLossFieldResolver {

    private var winLossPayloadMapping: Map<String, String>? = null
    private val winLossDataMap = mutableMapOf<String, WinLossData>()

    data class WinLossData(
        val isWin: Boolean,
        val lossReason: LossReason? = null,
        val winPrice: Double? = null,
        val additionalData: Map<String, Any> = emptyMap()
    )

    // "winLossNotificationPayloadConfig": {
    //    "accountId": "config.accountID",
    //    "applicationId": "sdk.app.bundle",
    //    "auctionId": "bidRequest.id",
    //    "bidder": "bid.ext.prebid.meta.adaptercode",
    //    "country": "bidRequest.device.geo.country",
    //    "cpm": "bid.price",
    //    "creativeId": "bid.creativeId",
    //    "dealId": "bid.dealid",
    //    "deviceName": "bidRequest.device.model",
    //    "deviceType": "sdk.deviceType",
    //    "floor": "bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].bidFloor",
    //    "lineItemId": "bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].lineItemId",
    //    "loopIndex": "sdk.loopIndex",
    //    "lossReason": "sdk.lossReason",
    //    "notificationType": "sdk.[win|loss]",
    //    "organizationId": "config.organizationID",
    //    "osName": "bidRequest.device.os",
    //    "osVersion": "bidRequest.device.osv",
    //    "placementId": "bidRequest.imp.tagid",
    //    "releaseVersion": "sdk.releaseVersion",
    //    "responseTimeMillis": "bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].responseTimeMillis",
    //    "roundId": "bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].round",
    //    "source": "sdk.sdk",
    //    "url": "sdk.[bid.nurl|bid.lurl]"
    //  }

    fun setConfig(config: Config) {
        winLossPayloadMapping = config.winLossNotificationPayloadMapping
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

    // {
    //    "bidder": "appnexus",
    //    "dealId": "deal-123456",
    //    "creativeId": "creative-789012",
    //    "cpm": "2.50",
    //    "responseTimeMillis": 125,
    //    "releaseVersion": "v1.2.3",
    //    "auctionId": "auction-abc123def456",
    //    "accountId": "acc-987654321",
    //    "organizationId": "org-123456789",
    //    "applicationId": "com.example.myapp",
    //    "placementId": "placement-banner-top",
    //    "deviceName": "iPhone 14 Pro",
    //    "deviceType": "mobile",
    //    "osName": "iOS",
    //    "osVersion": "17.1",
    //    "loopIndex": 1,
    //    "country": "AU",
    //    "roundId": "round-001",
    //    "lineItemId": "li-premium-banner",
    //    "floor": 1.25,
    //    "notificationType": "loss",
    //    "url": "https://rtb.appnexus.com/loss?auction=abc123&reason=outbid",
    //    "lossReason": "outbid",
    //    "source": "sdk"
    //  }
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
        return when {
            // Win/Loss specific fields
            fieldPath.startsWith("winLoss.") -> {
                when (val field = fieldPath.removePrefix("winLoss.")) {
                    "isWin" -> winLossData.isWin
                    "winPrice" -> winLossData.winPrice
                    "lossReason" -> winLossData.lossReason?.code
                    "lossReasonDescription" -> winLossData.lossReason?.description
                    "eventType" -> if (winLossData.isWin) "win" else "loss"
                    else -> {
                        // Check additional data
                        winLossData.additionalData[field]
                    }
                }
            }

            // Current bid specific fields (for the bid being processed)
            fieldPath.startsWith("currentBid.") -> {
                val field = fieldPath.removePrefix("currentBid.")
                winLossData.additionalData[field]
            }

            // Auction-wide bid fields
            fieldPath.startsWith("auction.") -> {
                val field = fieldPath.removePrefix("auction.")
                when (field) {
                    "totalBids" -> AuctionBidManager.getAllBids(auctionId).size
                    "winningBidPrice" -> AuctionBidManager.getWinningBidPrice(auctionId)
                    "winningBidId" -> AuctionBidManager.getWinningBid(auctionId)?.id
                    "winningNetwork" -> AuctionBidManager.getWinningBid(auctionId)?.adNetwork?.networkName
                    "highestBidPrice" -> AuctionBidManager.getAllBids(auctionId)
                        .mapNotNull { it.price?.toDouble() }
                        .maxOrNull()

                    "lowestBidPrice" -> AuctionBidManager.getAllBids(auctionId)
                        .mapNotNull { it.price?.toDouble() }
                        .minOrNull()

                    else -> null
                }
            }

            // Static values (literal strings)
            fieldPath.startsWith("\"") && fieldPath.endsWith("\"") -> {
                fieldPath.removeSurrounding("\"")
            }

            // Delegate to existing TrackingFieldResolver for bid, bidRequest, config, sdk fields
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
}

// Extension function to access TrackingFieldResolver functionality
private fun TrackingFieldResolver.resolveField(auctionId: String, fieldPath: String): Any? {
    return resolveFieldPublic(auctionId, fieldPath)
}
