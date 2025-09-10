package io.cloudx.sdk.internal.ads.banner

import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.TestMainDispatcherRule
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ads.banner.components.BannerAdLoader
import io.cloudx.sdk.internal.ads.banner.components.BannerLoadOutcome
import io.cloudx.sdk.internal.common.service.AppLifecycleService
import io.cloudx.sdk.internal.connectionstatus.ConnectionInfo
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.connectionstatus.ConnectionType
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTrackerNew
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BannerManagerImplOutcomeTest {

    @get:Rule
    val mainRule = TestMainDispatcherRule(StandardTestDispatcher())

    private val bannerVisibility = MutableStateFlow(true)
    private val appForeground = MutableStateFlow(true)

    private val mockConnection = mockk<ConnectionStatusService>(relaxed = true)
    private val mockAppLifecycle = mockk<AppLifecycleService>(relaxed = true)
    private val mockMetrics = mockk<MetricsTrackerNew>(relaxed = true)
    private val mockLoader = mockk<BannerAdLoader>(relaxed = true)
    private val mockListener = mockk<CloudXAdViewListener>(relaxed = true)

    private val mockBanner = mockk<BannerAdapterDelegate>(relaxed = true)
    private val bannerEvents = kotlinx.coroutines.flow.MutableSharedFlow<BannerAdapterDelegateEvent>(extraBufferCapacity = 1)
    private val bannerError = MutableStateFlow<io.cloudx.sdk.internal.adapter.CloudXAdapterError?>(null)

    private lateinit var mgr: BannerManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        CloudXLogger.isEnabled = false
        every { mockAppLifecycle.isResumed } returns appForeground
        coEvery { mockConnection.awaitConnection() } returns ConnectionInfo(false, ConnectionType.WIFI)

        // Provide non-null flows for banner presenter wiring
        every { mockBanner.event } returns bannerEvents
        every { mockBanner.lastErrorEvent } returns bannerError
    }

    @After
    fun tearDown() {
        if (this::mgr.isInitialized) mgr.destroy()
    }

    private fun createManager(refreshSeconds: Int = 1) {
        mgr = BannerManagerTestFactory(
            placementId = "pid",
            placementName = "pname",
            bannerVisibility = bannerVisibility,
            refreshSeconds = refreshSeconds,
            connectionStatusService = mockConnection,
            appLifecycleService = mockAppLifecycle,
            metricsTrackerNew = mockMetrics,
            loader = mockLoader
        )
        mgr.listener = mockListener
    }

    @Test
    fun `NoFill emits error and continues cadence`() = runTest(mainRule.dispatcher) {
        try {
            // First NoFill, then NoFill again
            coEvery { mockLoader.loadOnce() } returnsMany listOf(
                BannerLoadOutcome.NoFill,
                BannerLoadOutcome.NoFill
            )
            createManager(refreshSeconds = 1)

            // First request
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }
            verify { mockListener.onAdLoadFailed(any<CloudXAdError>()) }

            // Next interval → second request
            advanceTimeBy(1_000)
            runCurrent()
            coVerify(exactly = 2) { mockLoader.loadOnce() }
        } finally {
            if (this@BannerManagerImplOutcomeTest::mgr.isInitialized) mgr.destroy()
        }
    }

    @Test
    fun `TrafficControl (ADS_DISABLED) emits error and continues cadence`() = runTest(mainRule.dispatcher) {
        try {
            // First TrafficControl, then NoFill
            coEvery { mockLoader.loadOnce() } returnsMany listOf(
                BannerLoadOutcome.TrafficControl,
                BannerLoadOutcome.NoFill
            )
            createManager(refreshSeconds = 1)

            // First request
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }
            // Capture error to assert code 308
            val slot = slot<CloudXAdError>()
            verify { mockListener.onAdLoadFailed(capture(slot)) }
            assert(slot.captured.code == CLXErrorCode.ADS_DISABLED.code)

            // Next interval → second request
            advanceTimeBy(1_000)
            runCurrent()
            coVerify(exactly = 2) { mockLoader.loadOnce() }
        } finally {
            if (this@BannerManagerImplOutcomeTest::mgr.isInitialized) mgr.destroy()
        }
    }

    @Test
    fun `PermanentFailure tears down manager and stops cadence`() = runTest(mainRule.dispatcher) {
        try {
            coEvery { mockLoader.loadOnce() } returns BannerLoadOutcome.PermanentFailure
            createManager(refreshSeconds = 1)

            // First request triggers permanent failure
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }
            verify { mockListener.onAdLoadFailed(any()) }

            // Even after more time, should NOT fire again (manager destroyed)
            advanceTimeBy(5_000)
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }
        } finally {
            if (this@BannerManagerImplOutcomeTest::mgr.isInitialized) mgr.destroy()
        }
    }

    @Test
    fun `TransientFailure while visible emits error and waits full interval`() = runTest(mainRule.dispatcher) {
        try {
            // First request fails transiently, then second request succeeds or NoFill; we only care about cadence
            coEvery { mockLoader.loadOnce() } returnsMany listOf(
                BannerLoadOutcome.TransientFailure,
                BannerLoadOutcome.NoFill
            )

            createManager(refreshSeconds = 1)
            runCurrent()
            // First request happened and failed
            coVerify(exactly = 1) { mockLoader.loadOnce() }
            verify { mockListener.onAdLoadFailed(any()) }

            // Should not immediately retry; wait full interval
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }

            // After interval, next request fires
            advanceTimeBy(1_000)
            runCurrent()
            coVerify(exactly = 2) { mockLoader.loadOnce() }
        } finally {
            mgr.destroy()
        }
    }

    @Test
    fun `destroy clears prefetched and stops further actions`() = runTest(mainRule.dispatcher) {
        try {
            val deferred = CompletableDeferred<BannerLoadOutcome>()
            coEvery { mockLoader.loadOnce() } coAnswers { deferred.await() }
            createManager(refreshSeconds = 1)

            // Start visible and launch a request
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }

            // Hide during in-flight and then complete with success, creating a prefetched banner
            bannerVisibility.value = false
            runCurrent()
            deferred.complete(BannerLoadOutcome.Success(mockBanner))
            runCurrent()

            // Now destroy the manager → should destroy prefetched banner and stop
            mgr.destroy()
            verify { mockBanner.destroy() }

            // Even if we become visible later, nothing should display or request
            bannerVisibility.value = true
            runCurrent()
            verify(exactly = 0) { mockListener.onAdDisplayed(any()) }
            coVerify(exactly = 1) { mockLoader.loadOnce() }
        } finally {
            if (this@BannerManagerImplOutcomeTest::mgr.isInitialized) mgr.destroy()
        }
    }

    @Test
    fun `Success while hidden is prefetched and shown on visible`() = runTest(mainRule.dispatcher) {
        try {
            val deferred = CompletableDeferred<BannerLoadOutcome>()
            coEvery { mockLoader.loadOnce() } coAnswers { deferred.await() }
            createManager(refreshSeconds = 10) // keep cadence slow; we control completion

            // Start visible so the first request starts immediately
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }

            // Become hidden during in-flight
            bannerVisibility.value = false
            runCurrent()

            // Complete with success while hidden → manager should prefetch (no displayed yet)
            deferred.complete(BannerLoadOutcome.Success(mockBanner))
            runCurrent()
            verify(exactly = 0) { mockListener.onAdDisplayed(any()) }

            // Become visible → should show prefetched immediately
            bannerVisibility.value = true
            runCurrent()
            verify(exactly = 1) { mockListener.onAdDisplayed(any()) }
        } finally {
            if (this@BannerManagerImplOutcomeTest::mgr.isInitialized) mgr.destroy()
        }
    }
}
