package io.cloudx.sdk.internal.tracker

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TrackingFieldResolver.
 *
 * Tests the business-critical field resolution logic used for tracking/analytics payloads.
 *
 * Test Coverage (10 categories):
 * - Category 1: State Management - setConfig, setSessionConstData, clear
 * - Category 2: buildPayload() - joins resolved fields with semicolon
 * - Category 3: resolveField() - SDK fields (predefined parameters)
 * - Category 4: resolveField() - bid fields (with bidId filtering)
 * - Category 5: resolveField() - bidRequest fields
 * - Category 6: resolveField() - bidResponse fields
 * - Category 7: resolveField() - config fields
 * - Category 8: resolveNestedField() - deep path resolution with array filtering
 * - Category 9: handleIfaField() - privacy-aware IFA resolution
 * - Category 10: Placeholder expansion - ${...} template expansion
 */
class TrackingFieldResolverTest : CXTest() {

    @Before
    fun setUp() {
        // Mock ApplicationContext for PrivacyService
        mockkStatic(::ApplicationContext)
        val mockContext = mockk<Context>(relaxed = true)
        every { ApplicationContext(any()) } returns mockContext
        every { ApplicationContext() } returns mockContext

        // Clear state before each test
        TrackingFieldResolver.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
        TrackingFieldResolver.clear()
    }

    // ========== Category 1: State Management ==========

    @Test
    fun `setConfig - stores accountId and tracking list`() {
        // Given
        val config = mockk<Config>(relaxed = true)
        every { config.accountId } returns "account-123"
        every { config.trackers } returns listOf("field1", "field2")
        every { config.rawJson } returns JSONObject()

        // When
        TrackingFieldResolver.setConfig(config)

        // Then - accountId can be retrieved
        assertThat(TrackingFieldResolver.getAccountId()).isEqualTo("account-123")
    }

    @Test
    fun `clear - removes all stored data`() {
        // Given - data is set
        val auctionId = "auction-clear"
        TrackingFieldResolver.setRequestData(auctionId, JSONObject("""{"test": "value"}"""))
        TrackingFieldResolver.setResponseData(auctionId, JSONObject("""{"test": "value"}"""))
        TrackingFieldResolver.setSdkParam(auctionId, "sdk.param", "value")
        TrackingFieldResolver.setLoopIndex(auctionId, 1)

        // When
        TrackingFieldResolver.clear()

        // Then - all data is cleared
        assertThat(TrackingFieldResolver.resolveField(auctionId, "bidRequest.test")).isNull()
        assertThat(TrackingFieldResolver.resolveField(auctionId, "bidResponse.test")).isNull()
        assertThat(TrackingFieldResolver.resolveField(auctionId, "sdk.param")).isNull()
        assertThat(TrackingFieldResolver.resolveField(auctionId, "sdk.loopIndex")).isNull()
    }

    // ========== Category 2: buildPayload() ==========

    @Test
    fun `buildPayload - joins resolved field values with semicolon`() {
        // Given - tracking list configured
        val config = mockk<Config>(relaxed = true)
        every { config.accountId } returns "acc-1"
        every { config.trackers } returns listOf("sdk.sessionId", "sdk.releaseVersion", "sdk.deviceType")
        every { config.rawJson } returns JSONObject()
        TrackingFieldResolver.setConfig(config)

        TrackingFieldResolver.setSessionConstData("sess-1", "v1.0", "phone", "", "")

        // When
        val auctionId = "auction-payload"
        val payload = TrackingFieldResolver.buildPayload(auctionId)

        // Then - values joined with semicolon
        assertThat(payload).isEqualTo("sess-1;v1.0;phone")
    }

    @Test
    fun `buildPayload - handles missing field values with empty strings`() {
        // Given - tracking list with some missing fields
        val config = mockk<Config>(relaxed = true)
        every { config.accountId } returns "acc-2"
        every { config.trackers } returns listOf("sdk.sessionId", "bidRequest.missing", "sdk.deviceType")
        every { config.rawJson } returns JSONObject()
        TrackingFieldResolver.setConfig(config)

        TrackingFieldResolver.setSessionConstData("sess-2", "", "tablet", "", "")

        // When
        val auctionId = "auction-missing"
        val payload = TrackingFieldResolver.buildPayload(auctionId)

        // Then - missing fields become empty strings
        assertThat(payload).isEqualTo("sess-2;;tablet")
    }

    @Test
    fun `buildPayload - returns null when tracking list not configured`() {
        // Given - explicitly set config with null trackers to ensure clean state
        val config = mockk<Config>(relaxed = true)
        every { config.accountId } returns null
        every { config.trackers } returns null
        every { config.rawJson } returns null
        TrackingFieldResolver.setConfig(config)

        // When
        val payload = TrackingFieldResolver.buildPayload("auction-no-config")

        // Then - returns null
        assertThat(payload).isNull()
    }

    // ========== Category 3: resolveField() - SDK Fields ==========

    @Test
    fun `resolveField - resolves custom SDK parameter`() {
        // Given
        val auctionId = "auction-custom-sdk"
        TrackingFieldResolver.setSdkParam(auctionId, "sdk.customMetric", "123.45")

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.customMetric")

        // Then
        assertThat(result).isEqualTo("123.45")
    }

    // ========== Category 4: resolveField() - bid Fields ==========

    @Test
    fun `resolveField - resolves bid field with bidId`() {
        // Given - response with multiple bids
        val auctionId = "auction-bid"
        val responseJson = JSONObject("""
            {
                "id": "auction-bid",
                "seatbid": [
                    {
                        "bid": [
                            {"id": "bid-1", "price": 2.5},
                            {"id": "bid-2", "price": 3.0}
                        ]
                    }
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When - resolve specific bid's price
        val result = TrackingFieldResolver.resolveField(auctionId, "bid.price", "bid-2")

        // Then - returns correct bid's price (JSON returns numbers as BigDecimal, convert for comparison)
        assertThat(result.toString()).isEqualTo("3.0")
    }

    @Test
    fun `resolveField - returns null for bid field without bidId`() {
        // Given
        val auctionId = "auction-no-bidid"
        val responseJson = JSONObject("""
            {
                "seatbid": [{"bid": [{"id": "bid-1", "price": 1.0}]}]
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When - try to resolve bid field without providing bidId
        val result = TrackingFieldResolver.resolveField(auctionId, "bid.price", null)

        // Then - returns null
        assertThat(result).isNull()
    }

    @Test
    fun `resolveField - returns null for non-existent bidId`() {
        // Given
        val auctionId = "auction-wrong-bidid"
        val responseJson = JSONObject("""
            {
                "seatbid": [{"bid": [{"id": "bid-1", "price": 1.0}]}]
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When - try to resolve with wrong bidId
        val result = TrackingFieldResolver.resolveField(auctionId, "bid.price", "bid-999")

        // Then - returns null
        assertThat(result).isNull()
    }

    @Test
    fun `resolveField - resolves nested bid field`() {
        // Given
        val auctionId = "auction-nested-bid"
        val responseJson = JSONObject("""
            {
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-nested",
                                "ext": {
                                    "cloudx": {
                                        "rank": 1
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When - resolve nested field
        val result = TrackingFieldResolver.resolveField(auctionId, "bid.ext.cloudx.rank", "bid-nested")

        // Then
        assertThat(result).isEqualTo(1)
    }

    // ========== Category 5: resolveField() - bidRequest Fields ==========

    @Test
    fun `resolveField - resolves bidRequest field`() {
        // Given
        val auctionId = "auction-request"
        val requestJson = JSONObject("""
            {
                "id": "req-123",
                "imp": [
                    {
                        "id": "imp-1",
                        "banner": {
                            "w": 320,
                            "h": 50
                        }
                    }
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)

        // When - resolve simple field
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.id")

        // Then
        assertThat(result).isEqualTo("req-123")
    }

    @Test
    fun `resolveField - resolves nested bidRequest field with array`() {
        // Given
        val auctionId = "auction-request-nested"
        val requestJson = JSONObject("""
            {
                "imp": [
                    {
                        "id": "imp-1",
                        "banner": {
                            "w": 320
                        }
                    }
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)

        // When - resolve through array (takes first element)
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.imp.banner.w")

        // Then
        assertThat(result).isEqualTo(320)
    }

    // ========== Category 6: resolveField() - bidResponse Fields ==========

    @Test
    fun `resolveField - resolves bidResponse field`() {
        // Given
        val auctionId = "auction-response"
        val responseJson = JSONObject("""
            {
                "id": "resp-456",
                "bidid": "bid-xyz"
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "bidResponse.bidid")

        // Then
        assertThat(result).isEqualTo("bid-xyz")
    }

    // ========== Category 8: resolveNestedField() - Deep Path Resolution ==========

    @Test
    fun `resolveNestedField - resolves simple path`() {
        // Given
        val auctionId = "auction-simple-path"
        val json = JSONObject("""{"field": "value"}""")
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.field")

        // Then
        assertThat(result).isEqualTo("value")
    }

    @Test
    fun `resolveNestedField - resolves through array (takes first element)`() {
        // Given
        val auctionId = "auction-array"
        val json = JSONObject("""
            {
                "items": [
                    {"id": "first"},
                    {"id": "second"}
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When - path goes through array
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.items.id")

        // Then - gets first element
        assertThat(result).isEqualTo("first")
    }

    @Test
    fun `resolveNestedField - handles array filtering`() {
        // Given
        val auctionId = "auction-filter"
        val json = JSONObject("""
            {
                "users": [
                    {"name": "Alice", "role": "admin"},
                    {"name": "Bob", "role": "user"},
                    {"name": "Charlie", "role": "admin"}
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When - use array filter syntax users[role=admin]
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.users[role=admin].name")

        // Then - gets first matching element
        assertThat(result).isEqualTo("Alice")
    }

    @Test
    fun `resolveNestedField - returns null for non-existent filtered element`() {
        // Given
        val auctionId = "auction-no-match"
        val json = JSONObject("""
            {
                "users": [
                    {"name": "Alice", "role": "user"}
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When - filter doesn't match
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.users[role=admin].name")

        // Then - returns null
        assertThat(result).isNull()
    }

    @Test
    fun `resolveNestedField - returns null for empty array`() {
        // Given
        val auctionId = "auction-empty-array"
        val json = JSONObject("""{"items": []}""")
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.items.id")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `resolveNestedField - returns null for missing path segment`() {
        // Given
        val auctionId = "auction-missing-segment"
        val json = JSONObject("""{"field": "value"}""")
        TrackingFieldResolver.setRequestData(auctionId, json)

        // When - path includes non-existent segment
        val result = TrackingFieldResolver.resolveField(auctionId, "bidRequest.field.nonexistent")

        // Then
        assertThat(result).isNull()
    }

    // ========== Category 9: handleIfaField() - Privacy-Aware IFA Resolution ==========

    @Test
    fun `resolveField sdk_ifa - returns sessionId when shouldClearPersonalData is true`() {
        // Given - privacy requires PII removal
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns true

        val auctionId = "auction-privacy"
        TrackingFieldResolver.setSessionConstData("session-private", "", "", "", "")

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.ifa")

        // Then - returns sessionId instead of IFA
        assertThat(result).isEqualTo("session-private")
    }

    @Test
    fun `resolveField sdk_ifa - returns hashed user ID when LAT enabled and hashedUserId available`() {
        // Given - LAT enabled (dnt=1) and hashedUserId set
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns false

        mockkObject(SdkKeyValueState)
        every { SdkKeyValueState.hashedUserId } returns "hashed-user-123"

        val auctionId = "auction-lat"
        val requestJson = JSONObject("""
            {
                "device": {
                    "dnt": 1,
                    "ifa": "real-ifa-456"
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.ifa")

        // Then - returns hashedUserId
        assertThat(result).isEqualTo("hashed-user-123")
    }

    @Test
    fun `resolveField sdk_ifa - returns hashedGeoIp when LAT enabled and hashedUserId not available`() {
        // Given - LAT enabled but no hashedUserId
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns false

        mockkObject(SdkKeyValueState)
        every { SdkKeyValueState.hashedUserId } returns null

        val auctionId = "auction-geo"
        val requestJson = JSONObject("""
            {
                "device": {
                    "dnt": 1
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)
        TrackingFieldResolver.setHashedGeoIp("hashed-geo-789")

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.ifa")

        // Then - returns hashedGeoIp
        assertThat(result).isEqualTo("hashed-geo-789")
    }

    @Test
    fun `resolveField sdk_ifa - returns real IFA when LAT not enabled`() {
        // Given - LAT not enabled (dnt=0)
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns false

        val auctionId = "auction-ifa"
        val requestJson = JSONObject("""
            {
                "device": {
                    "dnt": 0,
                    "ifa": "real-ifa-abc"
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.ifa")

        // Then - returns real IFA
        assertThat(result).isEqualTo("real-ifa-abc")
    }

    @Test
    fun `resolveField sdk_ifa - handles missing device data`() {
        // Given - no device data
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns false

        val auctionId = "auction-no-device"
        TrackingFieldResolver.setRequestData(auctionId, JSONObject("{}"))

        // When
        val result = TrackingFieldResolver.resolveField(auctionId, "sdk.ifa")

        // Then - returns null
        assertThat(result).isNull()
    }

    // ========== Category 10: Placeholder Expansion ==========

    @Test
    fun `resolveField - expands placeholder in bid field path`() {
        // Given - response with adapter code in ext
        val auctionId = "auction-placeholder"
        val responseJson = JSONObject("""
            {
                "seatbid": [
                    {
                        "bid": [
                            {
                                "id": "bid-placeholder",
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
                            }
                        ]
                    }
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, responseJson)

        // When - use placeholder ${...} in path
        val result = TrackingFieldResolver.resolveField(
            auctionId,
            "bid.ext.\${bid.ext.prebid.meta.adaptercode}.rank",
            "bid-placeholder"
        )

        // Then - placeholder expanded to "cloudx", path becomes "bid.ext.cloudx.rank"
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `resolveField - expands multiple placeholders`() {
        // Given
        val auctionId = "auction-multi-placeholder"
        TrackingFieldResolver.setSessionConstData("sessionabc", "v10", "", "", "")

        val requestJson = JSONObject("""
            {
                "sessionabc": {
                    "v10": {
                        "value": "nested-value"
                    }
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, requestJson)

        // When - use multiple placeholders
        val result = TrackingFieldResolver.resolveField(
            auctionId,
            "bidRequest.\${sdk.sessionId}.\${sdk.releaseVersion}.value"
        )

        // Then - both placeholders expanded
        assertThat(result).isEqualTo("nested-value")
    }

    // ========== Integration Tests ==========

    @Test
    fun `integration - resolves unknown field prefix returns null`() {
        // Given
        val auctionId = "auction-unknown"

        // When - use unknown prefix
        val result = TrackingFieldResolver.resolveField(auctionId, "unknown.field")

        // Then - returns null
        assertThat(result).isNull()
    }

    // ========== Real Production Tracking Fields Integration Test ==========

    @Test
    fun `integration - resolves all production tracking fields from real Config API`() {
        // Given - setup with realistic production data
        val auctionId = "prod-auction-123"
        val placementId = "HAQAcct7xtKUylAVDqWIP"

        // Set session constants
        TrackingFieldResolver.setSessionConstData(
            sessionId = "KVyyaYSXgyoKnmmxwU9bK",
            sdkVersion = "1.2.3",
            deviceType = "phone",
            abTestGroup = "",
            appBundle = "com.example.app"
        )

        // Set config with production tracking fields
        val mockConfig = mockk<Config>(relaxed = true)
        val trackingFields = listOf(
            "bid.ext.prebid.meta.adaptercode",
            "bid.w",
            "bid.h",
            "bid.dealid",
            "bid.crid",
            "bid.price",
            "sdk.responseTimeMillis",
            "sdk.releaseVersion",
            "bidRequest.id",
            "config.accountID",
            "config.organizationID",
            "sdk.app.bundle",
            "bidRequest.imp.tagid",
            "bidRequest.device.model",
            "sdk.deviceType",
            "bidRequest.device.os",
            "bidRequest.device.osv",
            "sdk.sessionId",
            "sdk.ifa",
            "sdk.loopIndex",
            "sdk.testGroupName",
            "config.placements[id=${'$'}{bidRequest.imp.tagid}].name",
            "bidRequest.device.geo.country",
            "bid.ext.cloudx.test",
            "bidResponse.ext.cloudx.auction.participants[rank=${'$'}{bid.ext.cloudx.rank}].round",
            "bidResponse.ext.cloudx.auction.participants[rank=${'$'}{bid.ext.cloudx.rank}].lineItemId"
        )
        every { mockConfig.trackers } returns trackingFields
        every { mockConfig.accountId } returns "SMRG123_dc"
        every { mockConfig.organizationId } returns "SMRG123"
        every { mockConfig.rawJson } returns JSONObject("""
            {
                "accountID": "SMRG123_dc",
                "organizationID": "SMRG123",
                "placements": [
                    {"id": "$placementId", "name": "defaultBanner"}
                ]
            }
        """.trimIndent())
        TrackingFieldResolver.setConfig(mockConfig)

        // Set bid request with production-like structure
        val bidRequestJson = JSONObject("""
            {
                "id": "$auctionId",
                "imp": [
                    {
                        "tagid": "$placementId",
                        "banner": {"w": 320, "h": 50}
                    }
                ],
                "device": {
                    "ifa": "test-ifa-456",
                    "model": "iPhone14,2",
                    "os": "iOS",
                    "osv": "17.1",
                    "dnt": 0,
                    "geo": {
                        "country": "USA"
                    }
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setRequestData(auctionId, bidRequestJson)

        // Set bid response with cloudx auction data
        val bidResponseJson = JSONObject("""
            {
                "id": "$auctionId",
                "seatbid": [
                    {
                        "seat": "cloudx",
                        "bid": [
                            {
                                "id": "bid-456",
                                "impid": "imp-1",
                                "price": 2.5,
                                "w": 320,
                                "h": 50,
                                "dealid": "deal-789",
                                "crid": "creative-101",
                                "ext": {
                                    "prebid": {
                                        "meta": {
                                            "adaptercode": "cloudx"
                                        }
                                    },
                                    "cloudx": {
                                        "rank": 1,
                                        "test": false
                                    }
                                }
                            }
                        ]
                    }
                ],
                "ext": {
                    "cloudx": {
                        "auction": {
                            "participants": [
                                {
                                    "rank": 1,
                                    "round": 1,
                                    "lineItemId": "line-item-123"
                                }
                            ]
                        }
                    }
                }
            }
        """.trimIndent())
        TrackingFieldResolver.setResponseData(auctionId, bidResponseJson)

        // Set SDK params
        TrackingFieldResolver.setSdkParam(auctionId, "sdk.responseTimeMillis", "250")
        TrackingFieldResolver.setLoopIndex(auctionId, 0)

        // Mock privacy service
        mockkObject(PrivacyService())
        every { PrivacyService().shouldClearPersonalData() } returns false

        // When - build payload with production tracking fields
        val payload = TrackingFieldResolver.buildPayload(auctionId, "bid-456")

        // Then - verify payload contains all expected values
        assertThat(payload).isNotNull()
        val values = payload!!.split(";")
        assertThat(values).hasSize(26)

        // Verify critical field values (spot check)
        assertThat(values[0]).isEqualTo("cloudx") // bid.ext.prebid.meta.adaptercode
        assertThat(values[1]).isEqualTo("320") // bid.w
        assertThat(values[2]).isEqualTo("50") // bid.h
        assertThat(values[3]).isEqualTo("deal-789") // bid.dealid
        assertThat(values[4]).isEqualTo("creative-101") // bid.crid
        assertThat(values[5]).isEqualTo("2.5") // bid.price
        assertThat(values[6]).isEqualTo("250") // sdk.responseTimeMillis
        assertThat(values[7]).isEqualTo("1.2.3") // sdk.releaseVersion
        assertThat(values[8]).isEqualTo(auctionId) // bidRequest.id
        assertThat(values[9]).isEqualTo("SMRG123_dc") // config.accountID
        assertThat(values[10]).isEqualTo("SMRG123") // config.organizationID
        assertThat(values[11]).isEqualTo("com.example.app") // sdk.app.bundle
        assertThat(values[12]).isEqualTo(placementId) // bidRequest.imp.tagid
        assertThat(values[13]).isEqualTo("iPhone14,2") // bidRequest.device.model
        assertThat(values[14]).isEqualTo("phone") // sdk.deviceType
        assertThat(values[15]).isEqualTo("iOS") // bidRequest.device.os
        assertThat(values[16]).isEqualTo("17.1") // bidRequest.device.osv
        assertThat(values[17]).isEqualTo("KVyyaYSXgyoKnmmxwU9bK") // sdk.sessionId
        assertThat(values[18]).isEqualTo("test-ifa-456") // sdk.ifa (from bidRequest.device.ifa)
        assertThat(values[19]).isEqualTo("0") // sdk.loopIndex
        assertThat(values[20]).isEqualTo("") // sdk.testGroupName (empty)
        assertThat(values[21]).isEqualTo("defaultBanner") // config.placements[id=${bidRequest.imp.tagid}].name
        assertThat(values[22]).isEqualTo("USA") // bidRequest.device.geo.country
        assertThat(values[23]).isEqualTo("false") // bid.ext.cloudx.test
        assertThat(values[24]).isEqualTo("1") // bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].round
        assertThat(values[25]).isEqualTo("line-item-123") // bidResponse.ext.cloudx.auction.participants[rank=${bid.ext.cloudx.rank}].lineItemId
    }
}
