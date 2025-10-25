package io.cloudx.sdk.internal.initialization

import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.internal.AdNetwork
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AdapterFactoryResolver.
 *
 * Tests the graceful degradation when adapter modules are missing.
 * Business logic: Config API may return networks that aren't bundled in the APK.
 * SDK should continue working with available adapters, not crash.
 *
 * Key responsibilities:
 * - Handle missing/unknown adapter modules gracefully (no crash)
 * - Work with empty network sets
 * - Categorize banner factories by size support (Standard vs MREC)
 */
class AdapterFactoryResolverTest : CXTest() {

    private lateinit var subject: AdapterFactoryResolver

    @Before
    fun setUp() {
        subject = AdapterFactoryResolver()
    }

    // ========== Core Business Logic: Graceful Degradation ==========

    @Test
    fun `resolveBidAdNetworkFactories - handles unknown network gracefully`() = runTest {
        // Given - Config API returns unknown network (adapter not bundled in APK)
        val networks = setOf(AdNetwork.Unknown("NonExistentNetwork"))

        // When
        val result = subject.resolveBidAdNetworkFactories(networks)

        // Then - should return empty maps (no crash, graceful degradation)
        assertThat(result.initializers).isEmpty()
        assertThat(result.bidRequestExtrasProviders).isEmpty()
        assertThat(result.interstitials).isEmpty()
        assertThat(result.rewardedInterstitials).isEmpty()
        assertThat(result.stdBanners).isEmpty()
        assertThat(result.mrecBanners).isEmpty()
        assertThat(result.nativeAds).isEmpty()
    }

    @Test
    fun `resolveBidAdNetworkFactories - handles empty network set`() = runTest {
        // Given - Config API returns no bidders (edge case)
        val networks = emptySet<AdNetwork>()

        // When
        val result = subject.resolveBidAdNetworkFactories(networks)

        // Then - should return empty maps (no crash)
        assertThat(result.initializers).isEmpty()
        assertThat(result.bidRequestExtrasProviders).isEmpty()
        assertThat(result.interstitials).isEmpty()
        assertThat(result.rewardedInterstitials).isEmpty()
        assertThat(result.stdBanners).isEmpty()
        assertThat(result.mrecBanners).isEmpty()
        assertThat(result.nativeAds).isEmpty()
    }

    // ========== Banner Size Categorization Logic ==========

    @Test
    fun `resolveBidAdNetworkFactories - categorizes banners by size support`() = runTest {
        // Given - any network with potential banner factories
        val networks = setOf(AdNetwork.CloudX)

        // When
        val result = subject.resolveBidAdNetworkFactories(networks)

        // Then - stdBanners and mrecBanners should be distinct maps
        assertThat(result.stdBanners).isNotSameInstanceAs(result.mrecBanners)

        // If any banner factory is resolved, verify size support logic
        result.stdBanners.values.forEach { factory ->
            // Factory in stdBanners must support Standard (320x50) size
            assertThat(factory.sizeSupport).contains(io.cloudx.sdk.internal.AdViewSize.Standard)
        }

        result.mrecBanners.values.forEach { factory ->
            // Factory in mrecBanners must support MREC (300x250) size
            assertThat(factory.sizeSupport).contains(io.cloudx.sdk.internal.AdViewSize.MREC)
        }
    }
}
