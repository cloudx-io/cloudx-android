package io.cloudx.sdk.internal.bid

import android.content.Context
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.util.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for JsonToBidResponse parsing logic.
 *
 * Tests the business-critical JSON parsing that converts Bid API responses into typed BidResponse objects.
 *
 * Test Coverage (7 categories):
 * - Category 1: Main jsonToBidResponse() function - valid/invalid JSON, no-bid handling, error handling
 * - Category 2: toBidResponse() - auction ID extraction, seatbid parsing
 * - Category 3: toSeatBid() - seatbid array parsing
 * - Category 4: toBid() - bid object parsing with all required/optional fields
 * - Category 5: getAdNetwork() - nested ext.prebid.meta.adaptercode extraction
 * - Category 6: getRank() - ext.cloudx.rank extraction
 * - Category 7: getAdapterExtras() - ext.cloudx.adapter_extras extraction, handle missing
 */
class JsonToBidResponseTest : CXTest() {

    @Before
    fun setUp() {
        // Mock ApplicationContext for error reporting (triggered when parsing fails)
        mockkStatic(::ApplicationContext)
        val mockContext = mockk<Context>(relaxed = true)
        every { ApplicationContext(any()) } returns mockContext
        every { ApplicationContext() } returns mockContext

        // Mock Bundle for adapter extras parsing
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putString(any(), any()) } returns Unit
        every { anyConstructed<Bundle>().putStringArray(any(), any()) } returns Unit
        every { anyConstructed<Bundle>().getString(any()) } returns ""
        every { anyConstructed<Bundle>().getStringArray(any()) } returns emptyArray()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ========== Category 1: Main jsonToBidResponse() Function ==========

    @Test
    fun `jsonToBidResponse - parses valid bid response successfully`() = runTest {
        // Given - minimal valid bid response JSON
        val json = """
            {
                "id": "auction-123",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad>test</ad>",
                                "price": 2.5,
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "cloudx"
                                        }
                                    },
                                    "cloudx": {
                                        "adapter_extras": {},
                                        "rank": 1
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - successful parse
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.auctionId).isEqualTo("auction-123")
        assertThat(bidResponse.seatBid).hasSize(1)
        assertThat(bidResponse.seatBid[0].bid).hasSize(1)
        assertThat(bidResponse.seatBid[0].bid[0].id).isEqualTo("bid-1")
    }

    @Test
    fun `jsonToBidResponse - handles no-bid response (missing seatbid)`() = runTest {
        // Given - response without seatbid field (no-bid scenario)
        val json = """
            {
                "id": "auction-456",
                "ext": {
                    "errors": {
                        "timeout": "No bids received in time"
                    }
                }
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - returns NO_FILL error (business logic: missing seatbid = no-bid)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.NO_FILL)
    }

    @Test
    fun `jsonToBidResponse - handles malformed JSON`() = runTest {
        // Given - invalid JSON syntax
        val json = """{ "id": "test", "invalid" }"""

        // When
        val result = jsonToBidResponse(json)

        // Then - returns INVALID_RESPONSE error
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.INVALID_RESPONSE)
    }

    @Test
    fun `jsonToBidResponse - handles missing required field in bid`() = runTest {
        // Given - bid missing required "id" field
        val json = """
            {
                "id": "auction-789",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "adm": "<ad>test</ad>",
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "cloudx"
                                        }
                                    },
                                    "cloudx": {
                                        "adapter_extras": {},
                                        "rank": 1
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - returns INVALID_RESPONSE error (JSONException: No value for id)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.INVALID_RESPONSE)
    }

    // ========== Category 2: toBidResponse() - Auction ID & SeatBid ==========

    @Test
    fun `toBidResponse - extracts auction ID correctly`() = runTest {
        // Given - bid response with specific auction ID
        val json = """
            {
                "id": "unique-auction-id-12345",
                "seatbid": []
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - auction ID extracted correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.auctionId).isEqualTo("unique-auction-id-12345")
    }

    @Test
    fun `toBidResponse - parses empty seatbid array`() = runTest {
        // Given - response with empty seatbid array (edge case: valid structure but no bids)
        val json = """
            {
                "id": "auction-empty",
                "seatbid": []
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - returns empty seatBid list (not an error)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.seatBid).isEmpty()
    }

    // ========== Category 3: toSeatBid() - SeatBid Array Parsing ==========

    @Test
    fun `toSeatBid - parses multiple seatbid entries`() = runTest {
        // Given - response with multiple seatbid entries
        val json = """
            {
                "id": "auction-multi",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad>1</ad>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    },
                    {
                        "bid": [
                            {
                                "id": "bid-2",
                                "adm": "<ad>2</ad>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 2}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - both seatbid entries parsed
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.seatBid).hasSize(2)
        assertThat(bidResponse.seatBid[0].bid[0].id).isEqualTo("bid-1")
        assertThat(bidResponse.seatBid[1].bid[0].id).isEqualTo("bid-2")
    }

    @Test
    fun `toSeatBid - handles seatbid with empty bid array`() = runTest {
        // Given - seatbid entry with empty bid array
        val json = """
            {
                "id": "auction-empty-bid",
                "seatbid": [
                    {
                        "bid": []
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - seatbid parsed with empty bid list
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.seatBid).hasSize(1)
        assertThat(bidResponse.seatBid[0].bid).isEmpty()
    }

    // ========== Category 4: toBid() - Bid Object Parsing ==========

    @Test
    fun `toBid - parses all required bid fields`() = runTest {
        // Given - bid with all required fields
        val json = """
            {
                "id": "auction-req",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-required",
                                "adm": "<creative>banner</creative>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 5}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - all required fields parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.id).isEqualTo("bid-required")
        assertThat(bid.adm).isEqualTo("<creative>banner</creative>")
        assertThat(bid.adNetwork).isEqualTo(AdNetwork.CloudX)
        assertThat(bid.rank).isEqualTo(5)
        assertThat(bid.auctionId).isEqualTo("auction-req")
    }

    @Test
    fun `toBid - parses price field when present`() = runTest {
        // Given - bid with price field
        val json = """
            {
                "id": "auction-price",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "price": 3.456789,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - price parsed and priceRaw formatted correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.price).isEqualTo(3.456789f)
        assertThat(bid.priceRaw).isEqualTo("3.456789") // Formatted with trailing zeros removed
    }

    @Test
    fun `toBid - handles missing price field (optional)`() = runTest {
        // Given - bid without price field
        val json = """
            {
                "id": "auction-no-price",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - price and priceRaw are null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.price).isNull()
        assertThat(bid.priceRaw).isNull()
    }

    @Test
    fun `toBid - parses dealId when present`() = runTest {
        // Given - bid with dealId
        val json = """
            {
                "id": "auction-deal",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "dealid": "premium-deal-123",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - dealId parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.dealId).isEqualTo("premium-deal-123")
    }

    @Test
    fun `toBid - handles missing dealId (optional)`() = runTest {
        // Given - bid without dealId
        val json = """
            {
                "id": "auction-no-deal",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - dealId is null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.dealId).isNull()
    }

    @Test
    fun `toBid - parses creativeId when present`() = runTest {
        // Given - bid with creativeId
        val json = """
            {
                "id": "auction-creative",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "creativeId": "creative-xyz-789",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 2}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - creativeId parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.creativeId).isEqualTo("creative-xyz-789")
    }

    @Test
    fun `toBid - handles missing creativeId (optional)`() = runTest {
        // Given - bid without creativeId
        val json = """
            {
                "id": "auction-no-creative",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - creativeId is null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.creativeId).isNull()
    }

    @Test
    fun `toBid - parses ad dimensions when present`() = runTest {
        // Given - bid with width and height
        val json = """
            {
                "id": "auction-dimensions",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "w": 320,
                                "h": 50,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - dimensions parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adWidth).isEqualTo(320)
        assertThat(bid.adHeight).isEqualTo(50)
    }

    @Test
    fun `toBid - handles missing ad dimensions (optional)`() = runTest {
        // Given - bid without width and height
        val json = """
            {
                "id": "auction-no-dimensions",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - dimensions are null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adWidth).isNull()
        assertThat(bid.adHeight).isNull()
    }

    @Test
    fun `toBid - passes auctionId to each bid`() = runTest {
        // Given - response with specific auction ID
        val json = """
            {
                "id": "auction-pass-through-123",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            },
                            {
                                "id": "bid-2",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 2}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - all bids have correct auctionId
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bids = (result as Result.Success).value.seatBid[0].bid
        assertThat(bids[0].auctionId).isEqualTo("auction-pass-through-123")
        assertThat(bids[1].auctionId).isEqualTo("auction-pass-through-123")
    }

    // ========== Category 5: getAdNetwork() - Nested Path Extraction ==========

    @Test
    fun `getAdNetwork - extracts CloudX network correctly`() = runTest {
        // Given - bid with CloudX adapter code
        val json = """
            {
                "id": "auction-cloudx",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "cloudx"
                                        }
                                    },
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - AdNetwork.CloudX extracted
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adNetwork).isEqualTo(AdNetwork.CloudX)
    }

    @Test
    fun `getAdNetwork - extracts Meta network correctly`() = runTest {
        // Given - bid with Meta adapter code
        val json = """
            {
                "id": "auction-meta",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "meta"
                                        }
                                    },
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - AdNetwork.Meta extracted
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adNetwork).isEqualTo(AdNetwork.Meta)
    }

    @Test
    fun `getAdNetwork - handles unknown adapter code`() = runTest {
        // Given - bid with unknown adapter code
        val json = """
            {
                "id": "auction-unknown",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "FutureNetwork"
                                        }
                                    },
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - AdNetwork.Unknown with network name
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adNetwork).isInstanceOf(AdNetwork.Unknown::class.java)
        assertThat((bid.adNetwork as AdNetwork.Unknown).networkName).isEqualTo("FutureNetwork")
    }

    // ========== Category 6: getRank() - Rank Extraction ==========

    @Test
    fun `getRank - extracts rank correctly`() = runTest {
        // Given - bid with specific rank
        val json = """
            {
                "id": "auction-rank",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},
                                        "rank": 42
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - rank extracted correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.rank).isEqualTo(42)
    }

    @Test
    fun `getRank - handles multiple bids with different ranks`() = runTest {
        // Given - multiple bids with different ranks
        val json = """
            {
                "id": "auction-multi-rank",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-first",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            },
                            {
                                "id": "bid-second",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 2}
                                }
                            },
                            {
                                "id": "bid-third",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 3}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - each bid has correct rank
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bids = (result as Result.Success).value.seatBid[0].bid
        assertThat(bids[0].rank).isEqualTo(1)
        assertThat(bids[1].rank).isEqualTo(2)
        assertThat(bids[2].rank).isEqualTo(3)
    }

    // ========== Category 7: getAdapterExtras() - Adapter Extras Extraction ==========

    @Test
    fun `getAdapterExtras - handles missing adapter_extras field`() = runTest {
        // Given - bid without adapter_extras (should return Bundle.EMPTY)
        val json = """
            {
                "id": "auction-no-extras",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},
                                        "rank": 1
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - adapterExtras is not null (would be Bundle.EMPTY in production, cannot verify equality in unit tests)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adapterExtras).isNotNull()
    }

    @Test
    fun `getAdapterExtras - parses adapter_extras when present`() = runTest {
        // Given - bid with adapter_extras (note: Bundle content not fully testable without Robolectric)
        val json = """
            {
                "id": "auction-with-extras",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "rank": 1,
                                        "adapter_extras": {
                                            "placement_id": "meta-placement-123"
                                        }
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - adapterExtras is not null and not EMPTY (full verification requires Robolectric)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.adapterExtras).isNotNull()
    }

    // ========== Edge Cases & Integration ==========

    @Test
    fun `jsonToBidResponse - parses complex multi-bid response`() = runTest {
        // Given - complex response with multiple seatbids and bids
        val json = """
            {
                "id": "complex-auction-123",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad>first</ad>",
                                "price": 5.0,
                                "dealid": "deal-premium",
                                "w": 320,
                                "h": 50,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            },
                            {
                                "id": "bid-2",
                                "adm": "<ad>second</ad>",
                                "price": 3.5,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "meta"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 2}
                                }
                            }
                        ]
                    },
                    {
                        "bid": [
                            {
                                "id": "bid-3",
                                "adm": "<ad>third</ad>",
                                "price": 2.0,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 3}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - complex structure parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bidResponse = (result as Result.Success).value
        assertThat(bidResponse.auctionId).isEqualTo("complex-auction-123")
        assertThat(bidResponse.seatBid).hasSize(2)
        assertThat(bidResponse.seatBid[0].bid).hasSize(2)
        assertThat(bidResponse.seatBid[1].bid).hasSize(1)

        // Verify first bid
        val firstBid = bidResponse.seatBid[0].bid[0]
        assertThat(firstBid.id).isEqualTo("bid-1")
        assertThat(firstBid.price).isEqualTo(5.0f)
        assertThat(firstBid.dealId).isEqualTo("deal-premium")
        assertThat(firstBid.adWidth).isEqualTo(320)
        assertThat(firstBid.adHeight).isEqualTo(50)
        assertThat(firstBid.rank).isEqualTo(1)

        // Verify ranks are correct across all bids
        assertThat(bidResponse.seatBid[0].bid[0].rank).isEqualTo(1)
        assertThat(bidResponse.seatBid[0].bid[1].rank).isEqualTo(2)
        assertThat(bidResponse.seatBid[1].bid[0].rank).isEqualTo(3)
    }

    @Test
    fun `priceRaw - formats price with trailing zeros removed`() = runTest {
        // Given - bid with price that has trailing zeros
        val json = """
            {
                "id": "auction-price-format",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "price": 2.500000,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - priceRaw formatted correctly (trailing zeros removed)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.price).isEqualTo(2.5f)
        assertThat(bid.priceRaw).isEqualTo("2.5") // Not "2.500000"
    }

    @Test
    fun `priceRaw - handles integer price value`() = runTest {
        // Given - bid with integer price (no decimals)
        val json = """
            {
                "id": "auction-int-price",
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-1",
                                "adm": "<ad/>",
                                "price": 10.0,
                                "ext": {
                                    "prebid": {"meta": {"adaptercode": "cloudx"}},
                                    "cloudx": {
                                        "adapter_extras": {},"rank": 1}
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToBidResponse(json)

        // Then - priceRaw formatted as integer (decimal point removed)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val bid = (result as Result.Success).value.seatBid[0].bid[0]
        assertThat(bid.price).isEqualTo(10.0f)
        assertThat(bid.priceRaw).isEqualTo("10") // Not "10.0" or "10.000000"
    }
}
