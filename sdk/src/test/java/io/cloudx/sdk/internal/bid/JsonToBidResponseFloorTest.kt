package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.CXTest
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for bidFloor parsing from ext.cloudx.auction.participants[] in bid response JSON.
 *
 * Verifies that:
 * - getParticipants() correctly extracts floor prices by rank from the participants array
 * - Bids receive the correct bidFloor based on their rank (rank-based mapping)
 * - No-floor scenarios are normalized to null (bidFloor = 0, negative, null, or missing)
 * - Edge cases are handled (missing participants array, rank mismatches)
 */
class JsonToBidResponseFloorTest : CXTest() {

    @Test
    fun `parse bidFloor from participants - valid floor`() = runTest {
        val json = createBidResponseJson(bidFloorValue = "2.5")

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Valid floor (rank=1, bidFloor=2.5)")
        println("  Expected: 2.5")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertEquals(2.5f, bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - missing bidFloor field`() = runTest {
        val json = createBidResponseJson(includeBidFloor = false)

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Missing bidFloor field")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - null bidFloor`() = runTest {
        val json = createBidResponseJson(bidFloorValue = "null")

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Null bidFloor")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - zero bidFloor`() = runTest {
        val json = createBidResponseJson(bidFloorValue = "0")

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Zero bidFloor (normalized to null)")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - negative bidFloor`() = runTest {
        val json = createBidResponseJson(bidFloorValue = "-1")

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Negative bidFloor (normalized to null)")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - multiple bids with different floors`() = runTest {
        val json = """
        {
            "id": "auction123",
            "seatbid": [{
                "bid": [
                    {
                        "id": "bid1",
                        "adm": "adm1",
                        "price": 5.0,
                        "w": 320,
                        "h": 50,
                        "ext": {
                            "prebid": {
                                "meta": {
                                    "adaptercode": "meta"
                                }
                            },
                            "cloudx": {
                                "rank": 1
                            }
                        }
                    },
                    {
                        "id": "bid2",
                        "adm": "adm2",
                        "price": 4.5,
                        "w": 320,
                        "h": 50,
                        "ext": {
                            "prebid": {
                                "meta": {
                                    "adaptercode": "cloudx"
                                }
                            },
                            "cloudx": {
                                "rank": 2
                            }
                        }
                    }
                ]
            }],
            "ext": {
                "cloudx": {
                    "auction": {
                        "participants": [
                            {
                                "rank": 1,
                                "bidFloor": 3.0
                            },
                            {
                                "rank": 2,
                                "bidFloor": 1.5
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Multiple bids with different floors (rank-based mapping)")
        println("  Bid rank=1: Expected floor=3.0, Actual=${bidResponse.seatBid[0].bid[0].bidFloor}")
        println("  Bid rank=2: Expected floor=1.5, Actual=${bidResponse.seatBid[0].bid[1].bidFloor}")

        assertEquals(3.0f, bidResponse.seatBid[0].bid[0].bidFloor)
        assertEquals(1.5f, bidResponse.seatBid[0].bid[1].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - missing participants array`() = runTest {
        val json = """
        {
            "id": "auction123",
            "seatbid": [{
                "bid": [{
                    "id": "bid1",
                    "adm": "adm1",
                    "price": 5.0,
                    "w": 320,
                    "h": 50,
                    "ext": {
                        "prebid": {
                            "meta": {
                                "adaptercode": "cloudx"
                            }
                        },
                        "cloudx": {
                            "rank": 1
                        }
                    }
                }]
            }]
        }
        """.trimIndent()

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Missing participants array")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    @Test
    fun `parse bidFloor from participants - bid rank not in participants`() = runTest {
        val json = createBidResponseJson(participantRank = 1, bidRank = 2)

        val result = jsonToBidResponse(json)
        assert(result is Result.Success)
        val bidResponse = (result as Result.Success).value

        println("TEST: Bid rank=2 not in participants (only rank=1 exists)")
        println("  Expected: null")
        println("  Actual: ${bidResponse.seatBid[0].bid[0].bidFloor}")

        assertNull(bidResponse.seatBid[0].bid[0].bidFloor)
    }

    private fun createBidResponseJson(
        bidFloorValue: String = "2.5",
        includeBidFloor: Boolean = true,
        participantRank: Int = 1,
        bidRank: Int = 1
    ): String {
        val bidFloorField = if (includeBidFloor) {
            """"bidFloor": $bidFloorValue,"""
        } else {
            ""
        }

        return """
        {
            "id": "auction123",
            "seatbid": [{
                "bid": [{
                    "id": "bid1",
                    "adm": "adm1",
                    "price": 5.0,
                    "w": 320,
                    "h": 50,
                    "ext": {
                        "prebid": {
                            "meta": {
                                "adaptercode": "cloudx"
                            }
                        },
                        "cloudx": {
                            "rank": $bidRank
                        }
                    }
                }]
            }],
            "ext": {
                "cloudx": {
                    "auction": {
                        "participants": [{
                            "rank": $participantRank,
                            $bidFloorField
                            "bidder": "cloudx"
                        }]
                    }
                }
            }
        }
        """.trimIndent()
    }

}
