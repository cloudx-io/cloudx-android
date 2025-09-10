package io.cloudx.sdk.internal.ads.banner
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.TestMainDispatcherRule
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
class BannerManagerImplCadenceTest {

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

    private lateinit var mgr: BannerManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        // Disable logger to avoid touching android.util.Log in JVM unit tests
        CloudXLogger.isEnabled = false

        every { mockAppLifecycle.isResumed } returns appForeground
        coEvery { mockConnection.awaitConnection() } returns ConnectionInfo(false, ConnectionType.WIFI)
    }

    @Test
    fun `app foreground gating - fires only when app is resumed`() = runTest(mainRule.dispatcher) {
        try {
            // App starts in background though banner visibility is true
            appForeground.value = false
            coEvery { mockLoader.loadOnce() } returns BannerLoadOutcome.NoFill

            createManager(refreshSeconds = 1)
            runCurrent()

            // No request should fire while app is backgrounded
            coVerify(exactly = 0) { mockLoader.loadOnce() }

            // Let interval elapse while backgrounded (should queue hidden/foreground tick)
            advanceTimeBy(1_500)
            runCurrent()
            coVerify(exactly = 0) { mockLoader.loadOnce() }

            // Bring app to foreground → immediate request should fire
            appForeground.value = true
            runCurrent()
            coVerify(exactly = 1) { mockLoader.loadOnce() }
        } finally {
            mgr.destroy()
        }
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
    fun `no stacking after long in-flight`() = runTest(mainRule.dispatcher) {
        try {
        // Arrange: visible from start; first load takes > interval
        val outcomeDeferred = CompletableDeferred<BannerLoadOutcome>()
        coEvery { mockLoader.loadOnce() } coAnswers { outcomeDeferred.await() }
        createManager(refreshSeconds = 1)

        // Initial tick triggers first request immediately
        runCurrent()
        coVerify(exactly = 1) { mockLoader.loadOnce() }

        // Simulate in-flight work > interval
        advanceTimeBy(1_500)
        runCurrent()

        // Finish first request now (NoFill)
        outcomeDeferred.complete(BannerLoadOutcome.NoFill)
        runCurrent()

        // Should NOT immediately trigger next request (no stacking)
        coVerify(exactly = 1) { mockLoader.loadOnce() }

        // After full interval, next request should fire
        advanceTimeBy(1_000)
        runCurrent()
        coVerify(exactly = 2) { mockLoader.loadOnce() }
        } finally {
            mgr.destroy()
        }
    }

    @Test
    fun `hidden elapsed then visible during in-flight - no immediate on finish, next after interval`() = runTest(mainRule.dispatcher) {
        try {
        // Arrange: first request starts while visible, then we go hidden and let interval elapse
        val outcomeDeferred = CompletableDeferred<BannerLoadOutcome>()
        coEvery { mockLoader.loadOnce() } coAnswers { outcomeDeferred.await() } andThen
                BannerLoadOutcome.NoFill

        createManager(refreshSeconds = 1)
        runCurrent()
        coVerify(exactly = 1) { mockLoader.loadOnce() }

        // Go hidden during the in-flight request
        bannerVisibility.value = false
        runCurrent()

        // Let the interval elapse while hidden → cadence is paused (no hidden queue)
        advanceTimeBy(1_200)
        runCurrent()

        // Become visible again while still in-flight
        bannerVisibility.value = true
        runCurrent()

        // Complete the in-flight request now → should NOT start next immediately (no hidden queue)
        outcomeDeferred.complete(BannerLoadOutcome.NoFill)
        runCurrent()

        // After a full visible interval, next request should fire
        coVerify(exactly = 1) { mockLoader.loadOnce() }
        advanceTimeBy(1_000)
        runCurrent()
        coVerify(exactly = 2) { mockLoader.loadOnce() }
        } finally {
            mgr.destroy()
        }
    }
}
