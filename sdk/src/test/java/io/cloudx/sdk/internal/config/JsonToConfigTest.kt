package io.cloudx.sdk.internal.config

import android.content.Context
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.config.Config.Placement
import io.cloudx.sdk.internal.util.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for JsonToConfig parsing logic.
 *
 * Tests the business-critical JSON parsing that converts Config API responses into typed Config objects.
 *
 * Test Coverage (5 categories):
 * - Category 1: Main jsonToConfig() function - valid/invalid JSON, error handling
 * - Category 2: Endpoint config parsing - string vs A/B test object formats
 * - Category 3: Placement type parsing - all placement types + unknown type handling
 * - Category 4: Array parsers - bidders, geoHeaders (empty filtering), trackers
 * - Category 5: Metrics config - optional boolean flags with defaults
 */
class JsonToConfigTest : CXTest() {

    @Before
    fun setUp() {
        // Mock ApplicationContext for error reporting (triggered when parsing fails)
        mockkStatic(::ApplicationContext)
        val mockContext = mockk<Context>(relaxed = true)
        every { ApplicationContext(any()) } returns mockContext
        every { ApplicationContext() } returns mockContext

        // Mock Bundle for bidder initData parsing (relaxed to auto-mock all methods)
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

    // ========== Category 1: Main jsonToConfig() Function ==========

    @Test
    fun `jsonToConfig - parses valid JSON successfully`() = runTest {
        // Given - minimal valid config JSON
        val json = """
            {
                "appID": "test-app-123",
                "sessionID": "session-456",
                "preCacheSize": 5,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - successful parse
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.appId).isEqualTo("test-app-123")
        assertThat(config.sessionId).isEqualTo("session-456")
        assertThat(config.precacheSize).isEqualTo(5)
        assertThat(config.auctionEndpointUrl.default).isEqualTo("https://auction.example.com")
        assertThat(config.cdpEndpointUrl.default).isEqualTo("https://cdp.example.com")
    }

    @Test
    fun `jsonToConfig - handles malformed JSON`() = runTest {
        // Given - invalid JSON syntax
        val json = """{ "appID": "test", "invalid" }"""

        // When
        val result = jsonToConfig(json)

        // Then - returns INVALID_RESPONSE error
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.INVALID_RESPONSE)
    }

    @Test
    fun `jsonToConfig - handles missing required field`() = runTest {
        // Given - JSON missing required "appID" field
        val json = """
            {
                "sessionID": "session-456",
                "preCacheSize": 5,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - returns INVALID_RESPONSE error (JSONException: No value for appID)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).value
        assertThat(error.code).isEqualTo(CloudXErrorCode.INVALID_RESPONSE)
    }

    @Test
    fun `jsonToConfig - parses all optional fields`() = runTest {
        // Given - config with all optional fields
        val json = """
            {
                "appID": "test-app",
                "sessionID": "session-123",
                "preCacheSize": 3,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "impressionTrackerURL": "https://tracker.example.com",
                "winLossNotificationURL": "https://winloss.example.com",
                "geoDataEndpointURL": "https://geo.example.com",
                "organizationID": "org-123",
                "accountID": "acc-456",
                "appKeyOverride": "override-key",
                "bidders": [],
                "placements": [],
                "tracking": ["param1", "param2"],
                "winLossNotificationPayloadConfig": {"key1": "value1"},
                "geoHeaders": [],
                "keyValuePaths": {
                    "userKeyValues": "user.kv",
                    "appKeyValues": "app.kv",
                    "eids": "eids.path",
                    "placementLoopIndex": "placement.index"
                },
                "metrics": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - all optional fields parsed
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.trackingEndpointUrl).isEqualTo("https://tracker.example.com")
        assertThat(config.winLossNotificationUrl).isEqualTo("https://winloss.example.com")
        assertThat(config.geoDataEndpointUrl).isEqualTo("https://geo.example.com")
        assertThat(config.organizationId).isEqualTo("org-123")
        assertThat(config.accountId).isEqualTo("acc-456")
        assertThat(config.appKeyOverride).isEqualTo("override-key")
        assertThat(config.trackers).containsExactly("param1", "param2")
        assertThat(config.winLossNotificationPayloadConfig).containsEntry("key1", "value1")
        assertThat(config.keyValuePaths?.userKeyValues).isEqualTo("user.kv")
        assertThat(config.metrics).isNull()
    }

    // ========== Category 2: Endpoint Config Parsing (toEndpointConfig) ==========

    @Test
    fun `toEndpointConfig - parses string format`() = runTest {
        // Given - endpoint as simple string
        val json = JSONObject("""{"auctionEndpointURL": "https://simple.example.com"}""")

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - creates EndpointConfig with default only
        assertThat(result.default).isEqualTo("https://simple.example.com")
        assertThat(result.test).isNull()
    }

    @Test
    fun `toEndpointConfig - parses object format with A-B test`() = runTest {
        // Given - endpoint as object with test variants
        val json = JSONObject("""
            {
                "auctionEndpointURL": {
                    "default": "https://default.example.com",
                    "test": [
                        {"name": "variant-a", "value": "https://test-a.example.com", "ratio": 0.3},
                        {"name": "variant-b", "value": "https://test-b.example.com", "ratio": 0.2}
                    ]
                }
            }
        """.trimIndent())

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - creates EndpointConfig with test variants
        assertThat(result.default).isEqualTo("https://default.example.com")
        assertThat(result.test).hasSize(2)
        assertThat(result.test!![0].name).isEqualTo("variant-a")
        assertThat(result.test[0].value).isEqualTo("https://test-a.example.com")
        assertThat(result.test[0].ratio).isEqualTo(0.3)
        assertThat(result.test[1].name).isEqualTo("variant-b")
        assertThat(result.test[1].value).isEqualTo("https://test-b.example.com")
        assertThat(result.test[1].ratio).isEqualTo(0.2)
    }

    @Test
    fun `toEndpointConfig - handles missing test array in object format`() = runTest {
        // Given - endpoint object without test array
        val json = JSONObject("""
            {
                "auctionEndpointURL": {
                    "default": "https://default.example.com"
                }
            }
        """.trimIndent())

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - creates EndpointConfig with empty test list
        assertThat(result.default).isEqualTo("https://default.example.com")
        assertThat(result.test).isEmpty()
    }

    @Test
    fun `toEndpointConfig - handles missing ratio in test variant`() = runTest {
        // Given - test variant without ratio (should default to 1.0)
        val json = JSONObject("""
            {
                "auctionEndpointURL": {
                    "default": "https://default.example.com",
                    "test": [
                        {"name": "variant-a", "value": "https://test-a.example.com"}
                    ]
                }
            }
        """.trimIndent())

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - ratio defaults to 1.0
        assertThat(result.test).hasSize(1)
        assertThat(result.test!![0].ratio).isEqualTo(1.0)
    }

    @Test
    fun `toEndpointConfig - handles missing field`() = runTest {
        // Given - JSON without the requested field
        val json = JSONObject("""{"someOtherField": "value"}""")

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - returns EndpointConfig with empty default
        assertThat(result.default).isEmpty()
        assertThat(result.test).isNull()
    }

    @Test
    fun `toEndpointConfig - handles null field value`() = runTest {
        // Given - field explicitly set to null
        val json = JSONObject("""{"auctionEndpointURL": null}""")

        // When
        val result = json.toEndpointConfig("auctionEndpointURL")

        // Then - returns EndpointConfig with empty default
        assertThat(result.default).isEmpty()
        assertThat(result.test).isNull()
    }

    // ========== Category 3: Placement Type Parsing (toPlacements) ==========

    @Test
    fun `toPlacements - parses Banner placement`() = runTest {
        // Given - Banner placement JSON
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "banner-123",
                        "name": "main-banner",
                        "type": "BANNER",
                        "bannerRefreshRateMs": 45000,
                        "hasCloseButton": true
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - Banner placement parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val placement = config.placements["main-banner"]
        assertThat(placement).isInstanceOf(Config.Placement.Banner::class.java)
        val banner = placement as Config.Placement.Banner
        assertThat(banner.id).isEqualTo("banner-123")
        assertThat(banner.name).isEqualTo("main-banner")
        assertThat(banner.refreshRateMillis).isEqualTo(45000)
        assertThat(banner.hasCloseButton).isTrue()
    }

    @Test
    fun `toPlacements - parses MREC placement`() = runTest {
        // Given - MREC placement JSON
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "mrec-456",
                        "name": "main-mrec",
                        "type": "MREC",
                        "bannerRefreshRateMs": 60000
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - MREC placement parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val placement = config.placements["main-mrec"]
        assertThat(placement).isInstanceOf(Config.Placement.MREC::class.java)
        val mrec = placement as Config.Placement.MREC
        assertThat(mrec.id).isEqualTo("mrec-456")
        assertThat(mrec.name).isEqualTo("main-mrec")
        assertThat(mrec.refreshRateMillis).isEqualTo(60000)
    }

    @Test
    fun `toPlacements - parses Interstitial placement`() = runTest {
        // Given - Interstitial placement JSON
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "interstitial-789",
                        "name": "game-over",
                        "type": "INTERSTITIAL"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - Interstitial placement parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val placement = config.placements["game-over"]
        assertThat(placement).isInstanceOf(Config.Placement.Interstitial::class.java)
        val interstitial = placement as Config.Placement.Interstitial
        assertThat(interstitial.id).isEqualTo("interstitial-789")
        assertThat(interstitial.name).isEqualTo("game-over")
    }

    @Test
    fun `toPlacements - parses Rewarded placement`() = runTest {
        // Given - Rewarded placement JSON
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "rewarded-101",
                        "name": "extra-lives",
                        "type": "REWARDED"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - Rewarded placement parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val placement = config.placements["extra-lives"]
        assertThat(placement).isInstanceOf(Config.Placement.Rewarded::class.java)
        val rewarded = placement as Config.Placement.Rewarded
        assertThat(rewarded.id).isEqualTo("rewarded-101")
        assertThat(rewarded.name).isEqualTo("extra-lives")
    }

    @Test
    fun `toPlacements - parses Native placement with template type`() = runTest {
        // Given - Native placement JSON with small template
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "native-202",
                        "name": "feed-ad",
                        "type": "NATIVE",
                        "nativeTemplate": "small",
                        "bannerRefreshRateMs": 120000,
                        "hasCloseButton": false
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - Native placement parsed with template type
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val placement = config.placements["feed-ad"]
        assertThat(placement).isInstanceOf(Config.Placement.Native::class.java)
        val native = placement as Config.Placement.Native
        assertThat(native.id).isEqualTo("native-202")
        assertThat(native.name).isEqualTo("feed-ad")
        assertThat(native.templateType).isEqualTo(Config.Placement.Native.TemplateType.Small)
        assertThat(native.refreshRateMillis).isEqualTo(120000)
        assertThat(native.hasCloseButton).isFalse()
    }

    @Test
    fun `toPlacements - handles unknown placement type gracefully`() = runTest {
        // Given - Config with unknown placement type + valid placement
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "unknown-123",
                        "name": "future-type",
                        "type": "VIDEO_AD"
                    },
                    {
                        "id": "banner-456",
                        "name": "valid-banner",
                        "type": "BANNER"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - unknown type skipped, valid placement included
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.placements).hasSize(1)
        assertThat(config.placements["future-type"]).isNull()
        assertThat(config.placements["valid-banner"]).isNotNull()
    }

    @Test
    fun `toPlacements - handles case-insensitive placement types`() = runTest {
        // Given - placement types in lowercase (should still parse)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "banner-1",
                        "name": "lower-banner",
                        "type": "banner"
                    },
                    {
                        "id": "inter-1",
                        "name": "lower-interstitial",
                        "type": "interstitial"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - both parsed correctly (case-insensitive matching)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.placements).hasSize(2)
        assertThat(config.placements["lower-banner"]).isInstanceOf(Config.Placement.Banner::class.java)
        assertThat(config.placements["lower-interstitial"]).isInstanceOf(Config.Placement.Interstitial::class.java)
    }

    @Test
    fun `toPlacements - uses default refresh rate when missing`() = runTest {
        // Given - Banner without bannerRefreshRateMs field
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "banner-1",
                        "name": "default-banner",
                        "type": "BANNER"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - uses default refresh rate (30000ms)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val banner = config.placements["default-banner"] as Config.Placement.Banner
        assertThat(banner.refreshRateMillis).isEqualTo(30000)
    }

    @Test
    fun `toPlacements - uses default hasCloseButton when missing`() = runTest {
        // Given - placement without hasCloseButton field
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [
                    {
                        "id": "banner-1",
                        "name": "no-close-button",
                        "type": "BANNER"
                    }
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - hasCloseButton defaults to false
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        val banner = config.placements["no-close-button"] as Config.Placement.Banner
        assertThat(banner.hasCloseButton).isFalse()
    }

    // ========== Category 4: Array Parsers ==========

    @Test
    fun `toBidders - parses bidders array without crashing`() = runTest {
        // Given - config with bidders array
        // Note: Cannot fully test bidder parsing without Robolectric because toBundle() uses Android Bundle
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [
                    {
                        "networkName": "CloudX",
                        "initData": {}
                    }
                ],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - parsing succeeds (no crash)
        // Full bidder verification requires Robolectric due to Bundle usage
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `toBidders - handles empty bidders array`() = runTest {
        // Given - config with empty bidders array
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - returns empty bidders map
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.bidders).isEmpty()
    }

    @Test
    fun `toGeoHeaders - filters empty values`() = runTest {
        // Given - geoHeaders with empty source/target values
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "geoHeaders": [
                    {"source": "X-Country", "target": "country"},
                    {"source": "", "target": "empty-source"},
                    {"source": "X-City", "target": ""},
                    {"source": "X-Region", "target": "region"}
                ]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - only non-empty headers included
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.geoHeaders).hasSize(2)
        assertThat(config.geoHeaders!![0].source).isEqualTo("X-Country")
        assertThat(config.geoHeaders[0].target).isEqualTo("country")
        assertThat(config.geoHeaders[1].source).isEqualTo("X-Region")
        assertThat(config.geoHeaders[1].target).isEqualTo("region")
    }

    @Test
    fun `toGeoHeaders - handles missing geoHeaders field`() = runTest {
        // Given - config without geoHeaders field
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - geoHeaders is null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.geoHeaders).isNull()
    }

    @Test
    fun `toTrackers - parses tracker parameter list`() = runTest {
        // Given - config with tracking parameters
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "tracking": ["param1", "param2", "param3"]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - all tracker params parsed
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.trackers).containsExactly("param1", "param2", "param3").inOrder()
    }

    @Test
    fun `toTrackers - handles empty tracking array`() = runTest {
        // Given - config with empty tracking array
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "tracking": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - trackers is empty list
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.trackers).isEmpty()
    }

    // ========== Category 5: Metrics Config Parsing (toMetricsConfig) ==========

    @Test
    fun `toMetricsConfig - parses all enabled flags`() = runTest {
        // Given - metrics config with all flags enabled (array format)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "metrics": [{
                    "sendIntervalSeconds": 120,
                    "sdkAPICalls": {
                        "enabled": true
                    },
                    "networkCalls": {
                        "enabled": true,
                        "bidReq": {
                            "enabled": true
                        },
                        "initSdkReq": {
                            "enabled": true
                        },
                        "geoReq": {
                            "enabled": true
                        }
                    }
                }]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - all flags parsed as true
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sendIntervalSeconds).isEqualTo(120)
        assertThat(config.metrics.sdkApiCallsEnabled).isTrue()
        assertThat(config.metrics.networkCallsEnabled).isTrue()
        assertThat(config.metrics.networkCallsBidReqEnabled).isTrue()
        assertThat(config.metrics.networkCallsInitSdkReqEnabled).isTrue()
        assertThat(config.metrics.networkCallsGeoReqEnabled).isTrue()
    }

    @Test
    fun `toMetricsConfig - parses all disabled flags`() = runTest {
        // Given - metrics config with all flags disabled (array format)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "metrics": [{
                    "sdkAPICalls": {
                        "enabled": false
                    },
                    "networkCalls": {
                        "enabled": false,
                        "bidReq": {
                            "enabled": false
                        },
                        "initSdkReq": {
                            "enabled": false
                        },
                        "geoReq": {
                            "enabled": false
                        }
                    }
                }]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - all flags parsed as false
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sdkApiCallsEnabled).isFalse()
        assertThat(config.metrics.networkCallsEnabled).isFalse()
        assertThat(config.metrics.networkCallsBidReqEnabled).isFalse()
        assertThat(config.metrics.networkCallsInitSdkReqEnabled).isFalse()
        assertThat(config.metrics.networkCallsGeoReqEnabled).isFalse()
    }

    @Test
    fun `toMetricsConfig - uses null for missing optional flags`() = runTest {
        // Given - metrics config with only sendIntervalSeconds (array format)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "metrics": [{
                    "sendIntervalSeconds": 90
                }]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - optional flags are null (not set)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sendIntervalSeconds).isEqualTo(90)
        assertThat(config.metrics.sdkApiCallsEnabled).isNull()
        assertThat(config.metrics.networkCallsEnabled).isNull()
        assertThat(config.metrics.networkCallsBidReqEnabled).isNull()
        assertThat(config.metrics.networkCallsInitSdkReqEnabled).isNull()
        assertThat(config.metrics.networkCallsGeoReqEnabled).isNull()
    }

    @Test
    fun `toMetricsConfig - uses default send interval when missing`() = runTest {
        // Given - metrics config without sendIntervalSeconds (array format)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "metrics": [{
                    "sdkAPICalls": {
                        "enabled": true
                    }
                }]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - sendIntervalSeconds defaults to 60
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sendIntervalSeconds).isEqualTo(60)
    }

    @Test
    fun `toMetricsConfig - handles missing metrics field`() = runTest {
        // Given - config without metrics field
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": []
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - metrics is null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNull()
    }

    @Test
    fun `toMetricsConfig - handles partial flag configuration`() = runTest {
        // Given - metrics with some flags set, others missing (array format)
        val json = """
            {
                "appID": "test",
                "sessionID": "session",
                "preCacheSize": 1,
                "auctionEndpointURL": "https://auction.example.com",
                "cdpEndpointURL": "https://cdp.example.com",
                "bidders": [],
                "placements": [],
                "metrics": [{
                    "sdkAPICalls": {
                        "enabled": true
                    },
                    "networkCalls": {
                        "bidReq": {
                            "enabled": false
                        }
                    }
                }]
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(json)

        // Then - set flags have values, unset flags are null
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sdkApiCallsEnabled).isTrue()
        assertThat(config.metrics.networkCallsBidReqEnabled).isFalse()
        assertThat(config.metrics.networkCallsEnabled).isNull()
        assertThat(config.metrics.networkCallsInitSdkReqEnabled).isNull()
        assertThat(config.metrics.networkCallsGeoReqEnabled).isNull()
    }

    // ========== Integration Test: Real Production Config ==========

    @Test
    fun `jsonToConfig - parses real production Config API response`() = runTest {
        // Given - actual production Config API response
        val realConfigJson = """
            {
                "accountID":"SMRG123_dc",
                "organizationID":"SMRG123",
                "appID":"ZMXf_ucdY9w2oQRnilm0O",
                "sessionID":"KVyyaYSXgyoKnmmxwU9bK",
                "preCacheSize":5,
                "auctionEndpointURL":{"default":"https://au-dev.cloudx.io/openrtb2/auction"},
                "cdpEndpointURL":{"default":""},
                "geoDataEndpointURL":"https://geoip.cloudx.io",
                "geoHeaders":[
                    {"source":"cloudfront-viewer-country-iso3","target":"country"},
                    {"source":"cloudfront-viewer-city","target":"city"}
                ],
                "impressionTrackerURL":"https://tracker-stage.cloudx.io/t",
                "winLossNotificationURL":"https://au-dev.cloudx.io/notifications",
                "metrics":[{
                    "sendIntervalSeconds":60,
                    "sdkAPICalls":{"enabled":true},
                    "networkCalls":{
                        "enabled":true,
                        "bidReq":{"enabled":true},
                        "initSdkReq":{"enabled":true},
                        "geoReq":{"enabled":false}
                    }
                }],
                "bidders":[
                    {"initData":{},"networkName":"cloudx"},
                    {"initData":{"placementIds":["24378279391783950_24871772789101272"]},"networkName":"meta"}
                ],
                "keyValuePaths":{
                    "userKeyValues":"user.ext.data",
                    "appKeyValues":"app.ext.data",
                    "eids":"user.ext.eids[*]",
                    "placementLoopIndex":"imp[*].ext.data.loop-index"
                },
                "tracking":[
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
                ],
                "placements":[
                    {
                        "id":"HAQAcct7xtKUylAVDqWIP",
                        "name":"defaultBanner",
                        "bidResponseTimeoutMs":1000,
                        "adLoadTimeoutMs":3000,
                        "bannerRefreshRateMs":150000,
                        "type":"BANNER",
                        "nativeTemplate":"small"
                    },
                    {
                        "id":"S_r2JU1-wL3Q4AiLAPJnW",
                        "name":"mrec",
                        "bidResponseTimeoutMs":1000,
                        "adLoadTimeoutMs":3000,
                        "bannerRefreshRateMs":30000,
                        "type":"MREC"
                    }
                ],
                "winLossNotificationPayloadConfig":{
                    "accountId":"config.accountID",
                    "applicationId":"sdk.app.bundle",
                    "auctionId":"bidRequest.id",
                    "bid":"seatbid[0].bid[0]",
                    "cloudxExt":"bidResponse.ext.cloudx",
                    "country":"bidRequest.device.geo.country",
                    "deviceName":"bidRequest.device.model",
                    "deviceType":"sdk.deviceType",
                    "error":"sdk.error",
                    "loopIndex":"sdk.loopIndex",
                    "lossReasonCode":"sdk.lossReasonCode",
                    "notificationType":"sdk.[loadSuccess|renderSuccess|loss]",
                    "organizationId":"config.organizationID",
                    "osName":"bidRequest.device.os",
                    "osVersion":"bidRequest.device.osv",
                    "placementId":"bidRequest.imp.tagid",
                    "sdkVersion":"sdk.releaseVersion",
                    "source":"sdk.sdk"
                }
            }
        """.trimIndent()

        // When
        val result = jsonToConfig(realConfigJson)

        // Then - verify all critical fields are parsed correctly
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val config = (result as Result.Success).value

        // Core identifiers
        assertThat(config.accountId).isEqualTo("SMRG123_dc")
        assertThat(config.organizationId).isEqualTo("SMRG123")
        assertThat(config.appId).isEqualTo("ZMXf_ucdY9w2oQRnilm0O")
        assertThat(config.sessionId).isEqualTo("KVyyaYSXgyoKnmmxwU9bK")
        assertThat(config.precacheSize).isEqualTo(5)

        // Endpoint URLs
        assertThat(config.auctionEndpointUrl.default).isEqualTo("https://au-dev.cloudx.io/openrtb2/auction")
        assertThat(config.cdpEndpointUrl.default).isEqualTo("")
        assertThat(config.geoDataEndpointUrl).isEqualTo("https://geoip.cloudx.io")
        assertThat(config.trackingEndpointUrl).isEqualTo("https://tracker-stage.cloudx.io/t")
        assertThat(config.winLossNotificationUrl).isEqualTo("https://au-dev.cloudx.io/notifications")

        // Geo headers
        assertThat(config.geoHeaders).isNotNull()
        assertThat(config.geoHeaders!!).hasSize(2)
        assertThat(config.geoHeaders!![0].source).isEqualTo("cloudfront-viewer-country-iso3")
        assertThat(config.geoHeaders!![0].target).isEqualTo("country")
        assertThat(config.geoHeaders!![1].source).isEqualTo("cloudfront-viewer-city")
        assertThat(config.geoHeaders!![1].target).isEqualTo("city")

        // Bidders (Map<AdNetwork, Bidder>)
        assertThat(config.bidders).hasSize(2)
        assertThat(config.bidders[AdNetwork.CloudX]).isNotNull()
        assertThat(config.bidders[AdNetwork.Meta]).isNotNull()

        // Placements (sealed class hierarchy)
        // Note: Real config has type=BANNER with nativeTemplate, but parsing logic creates Banner (not Native)
        // because placement type is determined by "type" field, not "nativeTemplate"
        assertThat(config.placements).hasSize(2)
        assertThat(config.placements["defaultBanner"]?.id).isEqualTo("HAQAcct7xtKUylAVDqWIP")
        assertThat(config.placements["defaultBanner"]).isInstanceOf(Placement.Banner::class.java)
        val defaultBanner = config.placements["defaultBanner"] as Placement.Banner
        assertThat(defaultBanner.refreshRateMillis).isEqualTo(150000)

        assertThat(config.placements["mrec"]?.id).isEqualTo("S_r2JU1-wL3Q4AiLAPJnW")
        assertThat(config.placements["mrec"]).isInstanceOf(Placement.MREC::class.java)
        val mrec = config.placements["mrec"] as Placement.MREC
        assertThat(mrec.refreshRateMillis).isEqualTo(30000)

        // Tracking fields (26 fields total in production)
        assertThat(config.trackers).isNotNull()
        assertThat(config.trackers!!).hasSize(26)
        assertThat(config.trackers!!).contains("bid.ext.prebid.meta.adaptercode")
        assertThat(config.trackers!!).contains("sdk.ifa")
        assertThat(config.trackers!!).contains("config.placements[id=${'$'}{bidRequest.imp.tagid}].name")
        assertThat(config.trackers!!).contains("bidResponse.ext.cloudx.auction.participants[rank=${'$'}{bid.ext.cloudx.rank}].round")

        // Metrics config (array with nested objects)
        assertThat(config.metrics).isNotNull()
        assertThat(config.metrics!!.sendIntervalSeconds).isEqualTo(60)
        assertThat(config.metrics.sdkApiCallsEnabled).isTrue()
        assertThat(config.metrics.networkCallsEnabled).isTrue()
        assertThat(config.metrics.networkCallsBidReqEnabled).isTrue()
        assertThat(config.metrics.networkCallsInitSdkReqEnabled).isTrue()
        assertThat(config.metrics.networkCallsGeoReqEnabled).isFalse()

        // Win/loss notification payload config (critical for revenue tracking)
        // This is a Map<String, String> where keys are payload field names, values are field paths
        assertThat(config.winLossNotificationPayloadConfig).isNotEmpty()
        assertThat(config.winLossNotificationPayloadConfig["accountId"]).isEqualTo("config.accountID")
        assertThat(config.winLossNotificationPayloadConfig["auctionId"]).isEqualTo("bidRequest.id")
        assertThat(config.winLossNotificationPayloadConfig["bid"]).isEqualTo("seatbid[0].bid[0]")
        assertThat(config.winLossNotificationPayloadConfig["notificationType"]).isEqualTo("sdk.[loadSuccess|renderSuccess|loss]")
        assertThat(config.winLossNotificationPayloadConfig["source"]).isEqualTo("sdk.sdk")
    }
}
