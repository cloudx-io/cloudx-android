package io.cloudx.sdk.internal.config

import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for ResolvedEndpoints.
 *
 * Tests the A/B testing logic for endpoint URL selection based on random ratios.
 * Key responsibilities:
 * - Random-based endpoint selection using cumulative ratios
 * - Fallback to default URLs when no valid tests configured
 * - Geo endpoint assignment (no A/B testing)
 * - Test group name tracking for analytics
 */
class ResolvedEndpointsTest : CXTest() {

    private lateinit var random: Random

    @Before
    fun setUp() {
        random = mockk()
        ResolvedEndpoints.reset()
        ResolvedEndpoints.testGroupName = "" // Reset test group name (not reset by reset() method)
    }

    // ========== Section A: Default Behavior (No A/B Tests) ==========

    @Test
    fun `resolveFrom - uses defaults when no test variants configured`() = runTest {
        // Given - config with no test variants (random value doesn't matter, will use defaults)
        every { random.nextDouble() } returns 0.5
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = null
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should use default URLs
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1")
        assertThat(ResolvedEndpoints.geoEndpoint).isEqualTo("https://geo.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEmpty()
    }

    @Test
    fun `resolveFrom - uses defaults when test variants have empty values`() = runTest {
        // Given - test variants exist but have empty values (random value doesn't matter)
        every { random.nextDouble() } returns 0.5
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "test-group-a",
                        value = "", // Empty value
                        ratio = 1.0
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should fall back to defaults (test variants filtered out)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEmpty()
    }

    // ========== Section B: Auction Endpoint A/B Testing ==========

    @Test
    fun `resolveFrom - selects auction test variant when random value matches`() = runTest {
        // Given - auction A/B test with 50% ratio, random = 0.3 (within 0.5)
        every { random.nextDouble() } returns 0.3
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.5
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should select test variant
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction-test-a.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1") // default
        assertThat(ResolvedEndpoints.testGroupName).isEqualTo("auction-test-a")
    }

    @Test
    fun `resolveFrom - uses auction default when random value exceeds test ratio`() = runTest {
        // Given - auction A/B test with 50% ratio, random = 0.7 (exceeds 0.5)
        every { random.nextDouble() } returns 0.7
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.5
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should fall back to default (no test selected)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEmpty()
    }

    // ========== Section C: CDP Endpoint A/B Testing ==========

    @Test
    fun `resolveFrom - selects cdp test variant when random value matches`() = runTest {
        // Given - CDP A/B test with 50% ratio, random = 0.4 (within 0.5)
        every { random.nextDouble() } returns 0.4
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = null
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "cdp-test-b",
                        value = "https://cdp-test-b.cloudx.io/v1",
                        ratio = 0.5
                    )
                )
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should select CDP test variant
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1") // default
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp-test-b.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEqualTo("cdp-test-b")
    }

    @Test
    fun `resolveFrom - uses cdp default when random value exceeds test ratio`() = runTest {
        // Given - CDP A/B test with 50% ratio, random = 0.8 (exceeds 0.5)
        every { random.nextDouble() } returns 0.8
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = null
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "cdp-test-b",
                        value = "https://cdp-test-b.cloudx.io/v1",
                        ratio = 0.5
                    )
                )
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should fall back to default
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEmpty()
    }

    // ========== Section D: Combined A/B Testing (Auction + CDP) ==========

    @Test
    fun `resolveFrom - selects auction test when both tests configured and random matches auction first`() = runTest {
        // Given - both auction and CDP tests, random = 0.2 (matches auction's 0.3 ratio first)
        every { random.nextDouble() } returns 0.2
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.3
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "cdp-test-b",
                        value = "https://cdp-test-b.cloudx.io/v1",
                        ratio = 0.4
                    )
                )
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should select auction test (first in list with matching ratio)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction-test-a.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1") // default
        assertThat(ResolvedEndpoints.testGroupName).isEqualTo("auction-test-a")
    }

    @Test
    fun `resolveFrom - selects cdp test when both tests configured and random matches cdp second`() = runTest {
        // Given - both auction and CDP tests, random = 0.5 (exceeds auction 0.3, matches cumulative 0.3+0.4=0.7)
        every { random.nextDouble() } returns 0.5
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.3
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "cdp-test-b",
                        value = "https://cdp-test-b.cloudx.io/v1",
                        ratio = 0.4
                    )
                )
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should select CDP test (cumulative match)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1") // default
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp-test-b.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEqualTo("cdp-test-b")
    }

    @Test
    fun `resolveFrom - uses defaults when both tests configured but random exceeds all ratios`() = runTest {
        // Given - both tests configured, random = 0.9 (exceeds cumulative 0.3+0.4=0.7)
        every { random.nextDouble() } returns 0.9
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.3
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "cdp-test-b",
                        value = "https://cdp-test-b.cloudx.io/v1",
                        ratio = 0.4
                    )
                )
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - should fall back to defaults (no test selected)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction.cloudx.io/v1")
        assertThat(ResolvedEndpoints.cdpEndpoint).isEqualTo("https://cdp.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEmpty()
    }

    // ========== Section E: Geo Endpoint (No A/B Testing) ==========

    @Test
    fun `resolveFrom - always assigns geo endpoint directly from config`() = runTest {
        // Given - config with geo endpoint (random value doesn't affect geo)
        every { random.nextDouble() } returns 0.5
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = null
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - geo endpoint should be assigned directly (no A/B test)
        assertThat(ResolvedEndpoints.geoEndpoint).isEqualTo("https://geo.cloudx.io/v1")
    }

    @Test
    fun `resolveFrom - sets geo endpoint to empty string when null in config`() = runTest {
        // Given - config with null geo endpoint (random value doesn't affect geo)
        every { random.nextDouble() } returns 0.5
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = null
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = null,
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - geo endpoint should be empty string
        assertThat(ResolvedEndpoints.geoEndpoint).isEmpty()
    }

    // ========== Section F: Edge Cases ==========

    @Test
    fun `resolveFrom - handles exact boundary random values correctly`() = runTest {
        // Given - random exactly equals cumulative ratio (0.3)
        every { random.nextDouble() } returns 0.3
        val config = Config(
            appId = "app-123",
            sessionId = "session-456",
            precacheSize = 3,
            auctionEndpointUrl = Config.EndpointConfig(
                default = "https://auction.cloudx.io/v1",
                test = listOf(
                    Config.EndpointConfig.TestVariant(
                        name = "auction-test-a",
                        value = "https://auction-test-a.cloudx.io/v1",
                        ratio = 0.3
                    )
                )
            ),
            cdpEndpointUrl = Config.EndpointConfig(
                default = "https://cdp.cloudx.io/v1",
                test = null
            ),
            trackingEndpointUrl = null,
            geoDataEndpointUrl = "https://geo.cloudx.io/v1",
            winLossNotificationUrl = null,
            organizationId = null,
            appKeyOverride = null,
            accountId = null,
            bidders = emptyMap(),
            placements = emptyMap(),
            trackers = null,
            winLossNotificationPayloadConfig = emptyMap(),
            geoHeaders = null,
            keyValuePaths = null,
            metrics = null,
            rawJson = null
        )

        // When
        ResolvedEndpoints.resolveFrom(config, random)

        // Then - boundary value should be included in test group (<=)
        assertThat(ResolvedEndpoints.auctionEndpoint).isEqualTo("https://auction-test-a.cloudx.io/v1")
        assertThat(ResolvedEndpoints.testGroupName).isEqualTo("auction-test-a")
    }

}
