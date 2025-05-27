package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.Error
import io.cloudx.sdk.internal.Logger
import io.cloudx.sdk.internal.toAdNetwork
import io.cloudx.sdk.internal.toStringPairMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun jsonToBidResponse(json: String): Result<BidResponse, Error> =
    withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)

            if (!root.has("seatbid")) {
                val errorJson = root.optJSONObject("ext")?.optJSONObject("errors")?.toString()
                Logger.d(
                    "jsonToBidResponse",
                    "No seatbid — interpreting as no-bid. Ext errors: $errorJson"
                )
                return@withContext Result.Failure(Error("No bid."))
            }

            Result.Success(root.toBidResponse())
        } catch (e: Exception) {
            val errStr = e.toString()
            Logger.e(tag = "jsonToBidResponse", msg = errStr)
            Result.Failure(Error(errStr))
        }
    }

private fun JSONObject.toNoBidResponse(): NoBidResponse? {
    val nbrCode = "nbr".let { if (has(it)) getInt(it) else return null }

    return NoBidResponse(
        id = optString("id", ""),
        noBidResponseCode = nbrCode,
        ext = optString(EXT, "")
    )
}

private fun JSONObject.toBidResponse(): BidResponse {
    val auctionId = getString("id")

    return BidResponse(
        auctionId = auctionId,
        seatBid = getJSONArray("seatbid").toSeatBid(auctionId)
    )
}

private fun JSONArray.toSeatBid(auctionId: String): List<SeatBid> {
    val seatBids = mutableListOf<SeatBid>()
    val length = length()

    for (i in 0 until length) {
        val seatBid = getJSONObject(i)

        seatBids += SeatBid(
            seatBid.getJSONArray("bid").toBid(auctionId)
        )
    }

    return seatBids
}

private fun JSONArray.toBid(auctionId: String): List<Bid> {
    val bids = mutableListOf<Bid>()
    val length = length()

    for (i in 0 until length) {
        val bid = getJSONObject(i)

        bids += with(bid) {

            // This is a test HTML method.
//            val adm = testMethod(bid.getString("id"), getAdNetwork(), getString("adm"))
            val adm = getString("adm")

            val priceValue = if (has("price")) getDouble("price").toFloat() else null

            Bid(
                id = getString("id"),
                adm = adm,
                price = priceValue,
                priceRaw = priceValue?.let { "%.6f".format(it).trimEnd('0').trimEnd('.') },
                burl = if (has("burl")) getString("burl") else null,
                nurl = if (has("nurl")) getString("nurl") else null,
                adNetwork = getAdNetwork(),
                rank = getRank(),
                adapterExtras = getAdapterExtras(),
                dealId = if (has("dealid")) getString("dealid") else null,
                creativeId = if (has("creativeId")) getString("creativeId") else null,
                auctionId = auctionId,
                adWidth = if (has("w")) getInt("w") else null,
                adHeight = if (has("h")) getInt("h") else null,
            )
        }
    }

    return bids
}

private fun JSONObject.getAdNetwork(): AdNetwork =
    getJSONObject(EXT)
        .getJSONObject(CLOUDX)
        .getJSONObject(META)
        .getString(ADAPTER_CODE)
        .toAdNetwork()

private fun JSONObject.getRank(): Int =
    getJSONObject(EXT)
        .getJSONObject(CLOUDX)
        .getInt(RANK)

private fun JSONObject.getAdapterExtras(): Map<String, String> {
    val cloudX = getJSONObject(EXT)
        .getJSONObject(CLOUDX)

    val key = "adapter_extras"
    val adapterExtras = if (cloudX.has(key)) cloudX.getJSONObject(key) else null

    return adapterExtras?.toStringPairMap() ?: mapOf()
}

private const val EXT = "ext"
private const val CLOUDX = "cloudx"
private const val META = "meta"
private const val ADAPTER_CODE = "adaptercode"
private const val RANK = "rank"
