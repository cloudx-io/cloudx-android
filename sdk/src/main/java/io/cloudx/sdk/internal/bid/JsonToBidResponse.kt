package io.cloudx.sdk.internal.bid

import android.os.Bundle
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.toAdNetwork
import io.cloudx.sdk.internal.toBundle
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun jsonToBidResponse(json: String): Result<BidResponse, CloudXError> =
    withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)

            if (!root.has("seatbid")) {
                val errorJson = root.optJSONObject("ext")?.optJSONObject("errors")?.toString()
                CXLogger.d(
                    "jsonToBidResponse",
                    "No seatbid — interpreting as no-bid. Ext errors: $errorJson"
                )
                return@withContext Result.Failure(CloudXError(CloudXErrorCode.NO_FILL))
            }

            Result.Success(root.toBidResponse())
        } catch (e: Exception) {
            val errStr = e.toString()
            CXLogger.e(component = "jsonToBidResponse", message = errStr)
            Result.Failure(CloudXError(CloudXErrorCode.INVALID_RESPONSE))
        }
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

            val adm = getString("adm")

            val priceValue = if (has("price")) getDouble("price").toFloat() else null

            Bid(
                id = getString("id"),
                adm = adm,
                price = priceValue,
                priceRaw = priceValue?.let { "%.6f".format(it).trimEnd('0').trimEnd('.') },
                adNetwork = getAdNetwork(),
                rank = getRank(),
                adapterExtras = getAdapterExtras(),
                dealId = if (has("dealid")) getString("dealid") else null,
                creativeId = if (has("creativeId")) getString("creativeId") else null,
                auctionId = auctionId,
                adWidth = if (has("w")) getInt("w") else null,
                adHeight = if (has("h")) getInt("h") else null,
                rawJson = this
            )
        }
    }

    return bids
    // TEST MODE: Inject additional test bids for win/loss testing
    val testBids = createTestBids(auctionId)
    if (testBids.isNotEmpty()) {
        println("hop: model - INJECTING ${testBids.size} TEST BIDS into auction: $auctionId")
        testBids.forEachIndexed { index, testBid ->
            println("hop: model - Test Bid ${index + 1}: ${testBid.adNetwork.networkName} - \$${testBid.price} (rank: ${testBid.rank}) - ID: ${testBid.id}")
        }
        bids += testBids
    }

    println("hop: model - Total bids in response: ${bids.size}")
    bids.forEachIndexed { index, bid ->
        println("hop: model - Bid ${index + 1}: ${bid.adNetwork.networkName} - \$${bid.price} (rank: ${bid.rank}) - ID: ${bid.id}")
    }

    return bids
}

private fun createTestBids(auctionId: String): List<Bid> {
    // Create test bids with different scenarios
    val testBids = mutableListOf<Bid>()
    val colorHtmlTestGreen = "#D4EDDA" // Light green background
    val winnerAdm = """
        <div style="width:320px; height:50px; background-color:$colorHtmlTestGreen; display:flex; align-items:center; justify-content:center; border:2px solid #155724; border-radius:8px;">
            <span style="color:#155724; font-size:16px; font-weight:bold;">CloudX Winner Creative - $3.75 CPM</span>
        </div>
    """.trimIndent()

    // Test Bid 1: Google (will fail to load - simulated technical error)
    testBids += Bid(
        id = "test-bid-cloudx-winner",
        adm = winnerAdm,
        price = 3.75f,
        priceRaw = "3.75",
        adNetwork = AdNetwork.CloudX,
        rank = 1,
        adapterExtras = Bundle.EMPTY,
        dealId = "cloudx-deal-standard",
        creativeId = "cloudx-creative-winner",
        auctionId = auctionId,
        adWidth = 320,
        adHeight = 50,
        rawJson = JSONObject().apply {
            put("id", "test-bid-cloudx-winner")
            put("price", 3.75)
            put("nurl", "https://impression.cloudx.io/win?bid=winner&price=\${AUCTION_PRICE}")
            put("lurl", "https://impression.cloudx.io/loss?bid=winner&reason=\${AUCTION_LOSS}")
            put("burl", "https://impression.cloudx.io/billing?bid=winner&price=\${AUCTION_PRICE}")
            put("adm", winnerAdm)
            put("dealid", "cloudx-deal-standard")
            put("crid", "cloudx-creative-winner")
            put("ext", JSONObject().apply {
                put("cloudx", JSONObject().put("rank", 1))
                put("prebid", JSONObject().put("meta", JSONObject().put("adaptercode", "cloudx")))
            })
        }
    )
    testBids += Bid(
        id = "test-bid-google-001",
        adm = "<script>google ad script - WILL FAIL TO LOAD</scrasdipt>",
        price = 5.00f,
        priceRaw = "5.00",
        adNetwork = AdNetwork.CloudX,
        rank = 2,
        adapterExtras = Bundle.EMPTY,
        dealId = "google-deal-premium",
        creativeId = "google-creative-001",
        auctionId = auctionId,
        adWidth = 320,
        adHeight = 50,
        rawJson = JSONObject().apply {
            put("id", "test-bid-google-001")
            put("price", 5.00)
            put("nurl", "https://impression.google.com/win?bid=001&price=\${AUCTION_PRICE}")
            put("lurl", "https://impression.google.com/loss?bid=001&reason=\${AUCTION_LOSS}&price=\${AUCTION_PRICE}")
            put("burl", "https://impression.google.com/billing?bid=001&price=\${AUCTION_PRICE}")
            put("adm", "<script>google ad script - WILL FAIL TO LOAD</scriptasdasd>")
            put("dealid", "google-deal-premium")
            put("crid", "google-creative-001")
            put("ext", JSONObject().apply {
                put("cloudx", JSONObject().put("rank", 2))
                put("prebid", JSONObject().put("meta", JSONObject().put("adaptercode", "google")))
            })
        }
    )
//    // Test Bid 3: Facebook (will be outbid)
//    testBids += Bid(
//        id = "test-bid-facebook-003",
//        adm = "<div>facebook creative</div>",
//        price = 2.90f,
//        priceRaw = "2.90",
//        burl = "https://impression.facebook.com/billing?bid=003&price=\${AUCTION_PRICE}",
//        nurl = "https://impression.facebook.com/win?bid=003&price=\${AUCTION_PRICE}",
//        lurl = "https://impression.facebook.com/loss?bid=003&reason=\${AUCTION_LOSS}",
//        adNetwork = AdNetwork.CloudX,
//        rank = 3,
//        adapterExtras = Bundle.EMPTY,
//        dealId = null,
//        creativeId = "facebook-creative-003",
//        auctionId = auctionId,
//        adWidth = 320,
//        adHeight = 50,
//        rawJson = JSONObject().apply {
//            put("id", "test-bid-facebook-003")
//            put("price", 2.90)
//            put("nurl", "https://impression.facebook.com/win?bid=003&price=\${AUCTION_PRICE}")
//            put("lurl", "https://impression.facebook.com/loss?bid=003&reason=\${AUCTION_LOSS}")
//            put("burl", "https://impression.facebook.com/billing?bid=003&price=\${AUCTION_PRICE}")
//            put("adm", "<div>facebook creative</div>")
//            put("crid", "facebook-creative-003")
//            put("ext", JSONObject().apply {
//                put("cloudx", JSONObject().put("rank", 3))
//                put("prebid", JSONObject().put("meta", JSONObject().put("adaptercode", "facebook")))
//            })
//        }
//    )

    return testBids
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
