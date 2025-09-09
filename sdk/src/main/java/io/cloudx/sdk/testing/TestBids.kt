package io.cloudx.sdk.testing

import android.os.Bundle
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.bid.Bid

internal object TestBids {

    fun getTestBids(auctionId: String): List<Bid> {
        return listOf(
            Bid(
                id = "test-loser-1",
                adm = "<html><body>Test Ad 1</body></html>",
                price = 0.001f,
                priceRaw = "0.001",
                burl = null,
                nurl = null,
                lurl = "https://example.com/lurl/test-loser-1/lossPrice=\${AUCTION_PRICE}&lossReason=\${AUCTION_LOSS}",
                adNetwork = AdNetwork.CloudX, // or another test one
                rank = 2,
                adapterExtras = Bundle.EMPTY,
                dealId = null,
                creativeId = null,
                auctionId = auctionId,
                adWidth = 320,
                adHeight = 50
            ),
            Bid(
                id = "test-loser-2",
                adm = "<html><body>Test Ad 2</body></html>",
                price = 0.0005f,
                priceRaw = "0.0005",
                burl = null,
                nurl = null,
                lurl = "https://example.com/lurl/test-loser-2/lossPrice=\${AUCTION_PRICE}&lossReason=\${AUCTION_LOSS}",
                adNetwork = AdNetwork.CloudX,
                rank = 3,
                adapterExtras = Bundle.EMPTY,
                dealId = null,
                creativeId = null,
                auctionId = auctionId,
                adWidth = 320,
                adHeight = 50
            ),
            Bid(
                id = "test-loser-3",
                adm = "<html><body>Test Ad 4</body></html>",
                price = 0.0005f,
                priceRaw = "0.0005",
                burl = null,
                nurl = null,
                lurl = null,
                adNetwork = AdNetwork.CloudX,
                rank = 4,
                adapterExtras = Bundle.EMPTY,
                dealId = null,
                creativeId = null,
                auctionId = auctionId,
                adWidth = 320,
                adHeight = 50
            ),
        )
    }
}