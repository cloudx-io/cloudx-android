package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXDestroyable
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.bid.Bid

internal data class BidAdSourceResponse<T : CloudXDestroyable>(
    val bidItemsByRank: List<Item<T>>,
    val auctionId: String
) {

    data class Item<T>(
        val bid: Bid,
        val adNetwork: AdNetwork,
        val adNetworkOriginal: AdNetwork, // todo: only used for demo
        val createBidAd: suspend () -> T,
    )
}