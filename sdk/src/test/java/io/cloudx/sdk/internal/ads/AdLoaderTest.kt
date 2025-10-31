package io.cloudx.sdk.internal.ads

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.bid.Bid
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.tracker.win_loss.BidLifecycleEvent
import io.cloudx.sdk.internal.tracker.win_loss.WinLossTracker
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toSuccess
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class AdLoaderTest : CXTest() {

    private lateinit var mockBidAdSource: BidAdSource<CXAdapterDelegate>
    private lateinit var mockConnectionStatusService: ConnectionStatusService
    private lateinit var mockWinLossTracker: WinLossTracker
    private lateinit var mockErrorReportingService: ErrorReportingService
    private lateinit var adLoader: AdLoader<CXAdapterDelegate>

    @Before
    fun setUp() {
        mockBidAdSource = mockk()
        mockConnectionStatusService = mockk()
        mockWinLossTracker = mockk()
        mockErrorReportingService = mockk()

        coJustRun { mockConnectionStatusService.awaitConnection() }
        justRun { mockErrorReportingService.sendErrorEvent(any(), any()) }

        adLoader = AdLoader(
            placementName = "test-placement",
            placementId = "test-placement-id",
            bidAdSource = mockBidAdSource,
            bidAdLoadTimeoutMillis = 5000L,
            connectionStatusService = mockConnectionStatusService,
            winLossTracker = mockWinLossTracker,
            errorReportingService = mockErrorReportingService
        )
    }

    @Test
    fun `load - single ad success`() = runTest {
        // Given
        val successfulAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponse(successfulAd)
        val expectedBid = bidResponse.bidItemsByRank.first().bid
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).value).isEqualTo(successfulAd)

        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                expectedBid,
                BidLifecycleEvent.LOAD_SUCCESS,
                any(),
                any()
            )
        }
        verify(exactly = 0) { successfulAd.destroy() }
    }

    @Test
    fun `load - first ad succeeds, second ad loses`() = runTest {
        // Given
        val winningAd = createMockAd(loadResult = true)
        val losingAd = createMockAd(loadResult = true)
        val bidResponse = createBidResponse(winningAd, losingAd)
        val winningBid = bidResponse.bidItemsByRank[0].bid
        val losingBid = bidResponse.bidItemsByRank[1].bid
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).value).isEqualTo(winningAd)

        // Verify winning ad gets LOAD_SUCCESS event
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                winningBid,
                BidLifecycleEvent.LOAD_SUCCESS,
                any(),
                any()
            )
        }

        // Verify losing ad gets loss notification
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                losingBid,
                BidLifecycleEvent.LOSS,
                any(),
                any()
            )
        }

        // The successful ad should not be destroyed
        verify(exactly = 0) { winningAd.destroy() }
    }

    @Test
    fun `load - bid source failure`() = runTest {
        // Given
        val expectedError = CloudXError(CloudXErrorCode.NETWORK_ERROR, "Network failed")
        coEvery { mockBidAdSource.requestBid() } returns Result.Failure(expectedError)

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).value).isEqualTo(expectedError)
    }

    @Test
    fun `load - all ads fail returns no fill`() = runTest {
        // Given
        val failingAd1 = createMockAd(loadResult = false)
        val failingAd2 = createMockAd(loadResult = false)
        val bidResponse = createBidResponse(failingAd1, failingAd2)
        val expectedBid1 = bidResponse.bidItemsByRank[0].bid
        val expectedBid2 = bidResponse.bidItemsByRank[1].bid
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.NO_FILL)

        // Verify sendEvent is called for both failing ads
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                expectedBid1,
                BidLifecycleEvent.LOSS,
                any(),
                any()
            )
        }
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                expectedBid2,
                BidLifecycleEvent.LOSS,
                any(),
                any()
            )
        }

        // Both ads should have been destroyed
        verify { failingAd1.destroy() }
        verify { failingAd2.destroy() }
    }

    @Test
    fun `load - empty bid response returns no fill`() = runTest {
        // Given
        val emptyResponse = BidAdSourceResponse<CXAdapterDelegate>(
            bidItemsByRank = emptyList(),
            auctionId = "auction-empty"
        )
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(emptyResponse)

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.NO_FILL)
    }

    @Test
    fun `load - adapter creation throws exception`() = runTest {
        // Given
        val bidResponse = createBidResponse(createMockAd()).copy()
            .run {
                copy(bidItemsByRank = this.bidItemsByRank.map {
                    it.copy(createBidAd = {
                        throw RuntimeException(
                            "Ad creation failed"
                        )
                    })
                })
            }

        coEvery { mockBidAdSource.requestBid() } returns bidResponse.toSuccess()
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.NO_FILL)

        // Verify loss is recorded for the failed ad
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                bidResponse.bidItemsByRank[0].bid,
                BidLifecycleEvent.LOSS,
                any(),
                any()
            )
        }
    }

    @Test
    fun `load - adapter load throws exception`() = runTest {
        // Given
        val failingAd = createMockAd().apply {
            coEvery { load() } throws RuntimeException("Load failed")
        }
        val bidResponse = createBidResponse(failingAd)
        val expectedBid = bidResponse.bidItemsByRank.first().bid
        coEvery { mockBidAdSource.requestBid() } returns Result.Success(bidResponse)
        justRun { mockWinLossTracker.sendEvent(any(), any(), any(), any(), any()) }

        // When
        val result = adLoader.load()

        // Then
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.NO_FILL)

        // Verify loss is recorded and ad is destroyed
        verify {
            mockWinLossTracker.sendEvent(
                "auction-123",
                expectedBid,
                BidLifecycleEvent.LOSS,
                any(),
                any()
            )
        }
        verify { failingAd.destroy() }
    }


    private fun createMockAd(loadResult: Boolean = true): CXAdapterDelegate {
        return mockk<CXAdapterDelegate>().apply {
            coEvery { load() } returns loadResult
            justRun { timeout() }
            justRun { destroy() }
            every { placementName } returns "test-placement"
            every { bidderName } returns "TestBidder"
            every { placementId } returns "test-placement-id"
            every { externalPlacementId } returns null
            every { revenue } returns 1.0
            every { lastErrorEvent } returns MutableStateFlow(null)
        }
    }

    private fun createBidResponse(vararg ads: CXAdapterDelegate): BidAdSourceResponse<CXAdapterDelegate> {
        val items = ads.mapIndexed { index, ad ->
            BidAdSourceResponse.Item(
                bid = createBid(rank = index + 1, price = (10 - index).toFloat()),
                adNetwork = AdNetwork.CloudX,
                adNetworkOriginal = AdNetwork.CloudX,
                createBidAd = { ad }
            )
        }
        return BidAdSourceResponse(
            bidItemsByRank = items,
            auctionId = "auction-123"
        )
    }

    private fun createBid(rank: Int = 1, price: Float = 10.0f): Bid {
        return Bid(
            id = "bid-$rank",
            adm = "ad-markup-$rank",
            price = price,
            priceRaw = price.toString(),
            adNetwork = AdNetwork.CloudX,
            rank = rank,
            adapterExtras = mockk(relaxed = true),
            dealId = "deal-$rank",
            creativeId = "creative-$rank",
            auctionId = "auction-123",
            adWidth = 320,
            adHeight = 50,
            rawJson = JSONObject()
        )
    }
}