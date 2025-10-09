package io.cloudx.sdk.internal.bid

import android.os.Bundle
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.toAdNetwork
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toBundle
import io.cloudx.sdk.toFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun jsonToBidResponse(json: String): Result<BidResponse, CloudXError> =
    withContext(Dispatchers.IO) {
        val tag = "jsonToBidResponse"
        try {
            val root = JSONObject(json)

            if (!root.has("seatbid")) {
                val errorJson = root.optJSONObject("ext")?.optJSONObject("errors")?.toString()
                CXLogger.d(tag, "No seatbid — interpreting as no-bid. Ext errors: $errorJson")
                return@withContext CloudXErrorCode.NO_FILL.toFailure()
            }

            Result.Success(root.toBidResponse())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CXLogger.e(tag, "Failed to parse bid response", e)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Bid response JSON parsing failed: ${e.message}",
                errorDetails = e.stackTraceToString()
            )
            CloudXErrorCode.INVALID_RESPONSE.toFailure(cause = e)
        }
    }

private fun JSONObject.toBidResponse(): BidResponse {
    val auctionId = getString("id")
    val participants = getParticipants()

    return BidResponse(
        auctionId = auctionId,
        seatBid = getJSONArray("seatbid").toSeatBid(auctionId, participants)
    )
}

private fun JSONObject.getParticipants(): Map<Int, Float?> {
    val participantsMap = mutableMapOf<Int, Float?>()

    val ext = optJSONObject("ext") ?: return participantsMap
    val cloudx = ext.optJSONObject("cloudx") ?: return participantsMap
    val auction = cloudx.optJSONObject("auction") ?: return participantsMap
    val participants = auction.optJSONArray("participants") ?: return participantsMap

    for (i in 0 until participants.length()) {
        val participant = participants.getJSONObject(i)
        val rank = participant.optInt("rank", -1)
        if (rank == -1) continue

        // Agreed with BE: bidFloor == null or bidFloor == 0 means no floor
        val bidFloor = when {
            !participant.has("bidFloor") -> null
            participant.isNull("bidFloor") -> null
            else -> {
                val floor = participant.getDouble("bidFloor").toFloat()
                if (floor <= 0f) null else floor
            }
        }

        participantsMap[rank] = bidFloor
    }

    return participantsMap
}

private fun JSONArray.toSeatBid(auctionId: String, participants: Map<Int, Float?>): List<SeatBid> {
    val seatBids = mutableListOf<SeatBid>()
    val length = length()

    for (i in 0 until length) {
        val seatBid = getJSONObject(i)

        seatBids += SeatBid(
            seatBid.getJSONArray("bid").toBid(auctionId, participants)
        )
    }

    return seatBids
}

private fun JSONArray.toBid(auctionId: String, participants: Map<Int, Float?>): List<Bid> {
    val bids = mutableListOf<Bid>()
    val length = length()

    for (i in 0 until length) {
        val bid = getJSONObject(i)

        bids += with(bid) {

            val adm = getString("adm")

            val priceValue = if (has("price")) getDouble("price").toFloat() else null
            val rank = getRank()

            Bid(
                id = getString("id"),
                adm = adm,
                price = priceValue,
                priceRaw = priceValue?.let { "%.6f".format(it).trimEnd('0').trimEnd('.') },
                adNetwork = getAdNetwork(),
                rank = rank,
                adapterExtras = getAdapterExtras(),
                dealId = if (has("dealid")) getString("dealid") else null,
                creativeId = if (has("creativeId")) getString("creativeId") else null,
                auctionId = auctionId,
                adWidth = if (has("w")) getInt("w") else null,
                adHeight = if (has("h")) getInt("h") else null,
                rawJson = this,
                bidFloor = participants[rank]
            )
        }
    }

    return bids
}

private fun JSONObject.getAdNetwork(): AdNetwork =
    getJSONObject(EXT)
        .getJSONObject(PREBID)
        .getJSONObject(META)
        .getString(ADAPTER_CODE)
        .toAdNetwork()

private fun JSONObject.getRank(): Int =
    getJSONObject(EXT)
        .getJSONObject(CLOUDX)
        .getInt(RANK)

private fun JSONObject.getAdapterExtras(): Bundle {
    val cloudX = getJSONObject(EXT)
        .getJSONObject(CLOUDX)

    val key = "adapter_extras"
    val adapterExtras = if (cloudX.has(key)) cloudX.getJSONObject(key) else null

    return adapterExtras?.toBundle() ?: Bundle.EMPTY
}

private const val PREBID = "prebid"
private const val EXT = "ext"
private const val CLOUDX = "cloudx"
private const val META = "meta"
private const val ADAPTER_CODE = "adaptercode"
private const val RANK = "rank"
