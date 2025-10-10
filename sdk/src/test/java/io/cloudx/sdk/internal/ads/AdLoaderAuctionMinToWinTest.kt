package io.cloudx.sdk.internal.ads

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.win_loss.BidLifecycleEvent
import io.cloudx.sdk.internal.tracker.win_loss.LossReason
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

/**
 * Tests for AUCTION_MIN_TO_WIN calculation logic in AdLoader.
 *
 * Requirements from Liftoff:
 * - NURL (LOAD_SUCCESS):
 *   - Multiple bidders: 2nd highest bid + $0.01
 *   - Solo bid with floor: floor price
 *   - Solo bid without floor: $0.01
 * - LURL (LOSS): Winning bid's price
 *
 * These tests verify the end-to-end AUCTION_MIN_TO_WIN value calculation
 * passed to WinLossTracker.sendEvent() for different auction scenarios.
 */
class AdLoaderAuctionMinToWinTest : CXTest() {

    private lateinit var mockBidAdSource: BidAdSource<CXAdapterDelegate>
    private lateinit var mockConnectionStatusService: ConnectionStatusService
    private lateinit var mockWinLossTracker: WinLossTracker
    private lateinit var adLoader: AdLoader<CXAdapterDelegate>

    @Before
    fun setUp() {
        mockBidAdSource = mockk()
        mockConnectionStatusService = mockk()
        mockWinLossTracker = mockk()

        coJustRun { mockConnectionStatusService.awaitConnection() }
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        adLoader = AdLoader(
            placementName = "test-placement",
            placementId = "test-placement-id",
            bidAdSource = mockBidAdSource,
            bidAdLoadTimeoutMillis = 5000L,
            connectionStatusService = mockConnectionStatusService,
            winLossTracker = mockWinLossTracker
        )
    }

    @Test
    fun `NURL - multiple bidders - auctionMinToWin is 2nd highest plus 0_01`() = runTest {
        // Given: Liftoff bids $5.00, Meta bids $4.50
        val winningAd = createMockAd(loadResult = true)
        val losingAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponse(
            winningAd to 5.0f,
            losingAd to 4.5f
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOAD_SUCCESS event has auctionMinToWin = $4.50 + $0.01 = $4.51
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL multiple bidders")
        println("  Expected: 4.51 (2nd bid $4.50 + $0.01)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(4.51f)
    }

    @Test
    fun `NURL - solo bid with floor - auctionMinToWin is floor price`() = runTest {
        // Given: Liftoff bids $5.00, floor is $2.00, no other bidders
        val winningAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponseWithFloor(
            winningAd to 5.0f,
            bidFloor = 2.0f
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOAD_SUCCESS event has auctionMinToWin = $2.00
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL solo bid with floor")
        println("  Expected: 2.0 (floor price)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(2.0f)
    }

    @Test
    fun `NURL - solo bid without floor (null) - auctionMinToWin is 0_01`() = runTest {
        // Given: Liftoff bids $5.00, bidFloor = null, no other bidders
        val winningAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponseWithFloor(
            winningAd to 5.0f,
            bidFloor = null
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOAD_SUCCESS event has auctionMinToWin = $0.01
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL solo bid without floor (null)")
        println("  Expected: 0.01 (constant)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(0.01f)
    }

    @Test
    fun `NURL - solo bid with zero floor (normalized to null) - auctionMinToWin is 0_01`() = runTest {
        // Given: Liftoff bids $5.00, bidFloor = 0 (JSON parsing converts to null), no other bidders
        // Note: In production, JsonToBidResponse.getParticipants() converts bidFloor=0 to null
        val winningAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponseWithFloor(
            winningAd to 5.0f,
            bidFloor = null  // Normalized from 0
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOAD_SUCCESS event has auctionMinToWin = $0.01
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL solo bid with zero floor (normalized to null)")
        println("  Expected: 0.01 (0.0 normalized to null by JSON parsing)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(0.01f)
    }

    @Test
    fun `NURL - solo bid with negative floor (normalized to null) - auctionMinToWin is 0_01`() = runTest {
        // Given: Liftoff bids $5.00, bidFloor = -1 (JSON parsing converts to null), no other bidders
        // Note: In production, JsonToBidResponse.getParticipants() converts negative bidFloor to null
        val winningAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponseWithFloor(
            winningAd to 5.0f,
            bidFloor = null  // Normalized from -1
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOAD_SUCCESS event has auctionMinToWin = $0.01
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL solo bid with negative floor (normalized to null)")
        println("  Expected: 0.01 (negative normalized to null by JSON parsing)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(0.01f)
    }

    @Test
    fun `LURL - LOST_TO_HIGHER_BID - auctionMinToWin is winning bid price`() = runTest {
        // Given: Liftoff wins with $5.00, Meta loses with $4.50
        val winningAd = createMockAd(loadResult = true)
        val losingAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponse(
            winningAd to 5.0f,
            losingAd to 4.5f
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify LOSS event has auctionMinToWin = $5.00 (winner's price)
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOSS,
                LossReason.LOST_TO_HIGHER_BID,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: LURL LOST_TO_HIGHER_BID")
        println("  Expected: 5.0 (winning bid price)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(5.0f)
    }

    @Test
    fun `NURL - multiple bidders with 2nd bid missing price - auctionMinToWin is 0_01`() = runTest {
        // Given: Winning bid $5.00, second bid has null price
        val winningAd = createMockAd(loadResult = true)
        val losingAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponse(
            winningAd to 5.0f,
            losingAd to null
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)

        val auctionMinToWinSlot = slot<Float>()

        // When
        adLoader.load()

        // Then: Verify auctionMinToWin = 0 + $0.01 = $0.01
        verify {
            mockWinLossTracker.sendEvent(
                any(),
                any(),
                BidLifecycleEvent.LOAD_SUCCESS,
                LossReason.BID_WON,
                capture(auctionMinToWinSlot)
            )
        }

        println("TEST: NURL multiple bidders with 2nd bid null price")
        println("  Expected: 0.01 (0.0 + $0.01)")
        println("  Actual: ${auctionMinToWinSlot.captured}")

        assertThat(auctionMinToWinSlot.captured).isWithin(0.001f).of(0.01f)
    }

    // Helper functions

    private fun createMockAd(loadResult: Boolean): CXAdapterDelegate {
        val mockAd = mockk<CXAdapterDelegate>()
        coEvery { mockAd.load() } returns loadResult
        justRun { mockAd.destroy() }
        justRun { mockAd.timeout() }
        return mockAd
    }

    private fun createBidResponse(
        vararg adsWithPrices: Pair<CXAdapterDelegate, Float?>
    ): BidAdSourceResponse<CXAdapterDelegate> {
        val items = adsWithPrices.mapIndexed { index, (ad, price) ->
            BidAdSourceResponse.Item(
                bid = createBid(rank = index + 1, price = price, bidFloor = null),
                adNetwork = AdNetwork.CloudX,
                adNetworkOriginal = AdNetwork.CloudX,
                createBidAd = { ad }
            )
        }
        return BidAdSourceResponse(items, "auction-123")
    }

    private fun createBidResponseWithFloor(
        adWithPrice: Pair<CXAdapterDelegate, Float?>,
        bidFloor: Float?
    ): BidAdSourceResponse<CXAdapterDelegate> {
        val (ad, price) = adWithPrice
        val items = listOf(
            BidAdSourceResponse.Item(
                bid = createBid(rank = 1, price = price, bidFloor = bidFloor),
                adNetwork = AdNetwork.CloudX,
                adNetworkOriginal = AdNetwork.CloudX,
                createBidAd = { ad }
            )
        )
        return BidAdSourceResponse(items, "auction-123")
    }

    private fun createBid(rank: Int, price: Float?, bidFloor: Float?): Bid {
        return Bid(
            id = "bid-$rank",
            adm = "ad-markup-$rank",
            price = price,
            priceRaw = price?.toString(),
            adNetwork = AdNetwork.CloudX,
            rank = rank,
            adapterExtras = Bundle(),
            dealId = "deal-$rank",
            creativeId = "creative-$rank",
            auctionId = "auction-123",
            adWidth = 320,
            adHeight = 50,
            rawJson = JSONObject(),
            bidFloor = bidFloor
        )
    }
}
