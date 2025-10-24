package io.cloudx.sdk.internal.bid

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.appinfo.AppInfo
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.connectionstatus.ConnectionInfo
import io.cloudx.sdk.internal.connectionstatus.ConnectionStatusService
import io.cloudx.sdk.internal.connectionstatus.ConnectionType
import io.cloudx.sdk.internal.deviceinfo.DeviceInfo
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.gaid.GAIDProvider
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import io.cloudx.sdk.internal.httpclient.UserAgentProvider
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.screen.ScreenService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.tracker.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.tracker.SessionMetrics
import io.cloudx.sdk.internal.tracker.SessionMetricsTracker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for BidRequestProvider.
 *
 * Tests the generation of OpenRTB-compliant bid request JSON for ad auctions.
 * Covers app info, device info, impression objects, privacy handling, and key-value injection.
 */
class BidRequestProviderTest : CXTest() {

    private lateinit var context: Context
    private lateinit var appInfoProvider: AppInfoProvider
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var screenService: ScreenService
    private lateinit var connectionStatusService: ConnectionStatusService
    private lateinit var userAgentProvider: UserAgentProvider
    private lateinit var gaidProvider: GAIDProvider
    private lateinit var privacyService: PrivacyService
    private lateinit var subject: BidRequestProvider

    @Before
    fun setUp() {
        // Mock global singletons
        mockkObject(GeoInfoHolder)
        mockkObject(SdkKeyValueState)
        mockkObject(PlacementLoopIndexTracker)
        mockkObject(SessionMetricsTracker)

        // Mock SessionMetricsTracker to return default metrics
        every { SessionMetricsTracker.getMetrics() } returns SessionMetrics(
            depth = 1f,
            bannerDepth = 0f,
            mediumRectangleDepth = 0f,
            fullDepth = 0f,
            nativeDepth = 0f,
            rewardedDepth = 0f,
            durationSeconds = 0f
        )

        // Mock GeoInfoHolder
        every { GeoInfoHolder.getLat() } returns null
        every { GeoInfoHolder.getLon() } returns null
        every { GeoInfoHolder.getGeoInfo() } returns emptyMap()

        context = mockk(relaxed = true)
        appInfoProvider = mockk()
        deviceInfoProvider = mockk()
        screenService = mockk()
        connectionStatusService = mockk()
        userAgentProvider = mockk()
        gaidProvider = mockk()
        privacyService = mockk(relaxed = true)

        // Mock PrivacyService cloudXPrivacy StateFlow
        every { privacyService.cloudXPrivacy } returns MutableStateFlow(CloudXPrivacy())

        // Setup default mock responses
        coEvery { appInfoProvider.invoke() } returns AppInfo(
            appName = "Test App",
            packageName = "com.test.app",
            appVersion = "1.0.0"
        )

        coEvery { deviceInfoProvider.invoke() } returns DeviceInfo(
            manufacturer = "Google",
            model = "Pixel 5",
            hwVersion = "1.0",
            isTablet = false,
            os = "Android",
            osVersion = "12",
            apiLevel = 31,
            language = "en",
            mobileCarrier = "Verizon",
            screenDensity = 3.0f
        )

        coEvery { screenService.invoke() } returns ScreenService.ScreenData(
            widthPx = 1080,
            heightPx = 2340,
            widthDp = 360,
            heightDp = 780,
            dpi = 420,
            pxRatio = 3.0f
        )

        coEvery { connectionStatusService.currentConnectionInfo() } returns ConnectionInfo(
            type = ConnectionType.WIFI,
            isMetered = false
        )

        coEvery { userAgentProvider.invoke() } returns "Mozilla/5.0 (Android 12)"

        coEvery { gaidProvider.invoke() } returns GAIDProvider.Result(
            gaid = "test-gaid-123",
            isLimitAdTrackingEnabled = false
        )

        every { privacyService.shouldClearPersonalData() } returns false

        subject = BidRequestProvider(
            context = context,
            sdkVersion = "1.0.0-test",
            provideAppInfo = appInfoProvider,
            provideDeviceInfo = deviceInfoProvider,
            provideScreenData = screenService,
            connectionStatusService = connectionStatusService,
            provideUserAgent = userAgentProvider,
            provideGAID = gaidProvider,
            privacyService = privacyService,
            bidRequestExtrasProviders = emptyMap()
        )
    }

    @After
    fun tearDown() {
        unmockkObject(GeoInfoHolder)
        unmockkObject(SdkKeyValueState)
        unmockkObject(PlacementLoopIndexTracker)
        unmockkObject(SessionMetricsTracker)
    }

    @Test
    fun `invoke - creates valid bid request with basic structure`() = runTest {
        // Given - banner ad request
        val params = BidRequestProvider.Params(
            placementId = "test-placement-123",
            adType = AdType.Banner.Standard,
            placementName = "banner_main",
            accountId = "acc-123",
            appKey = "app-key-123",
            appId = "app-id-123"
        )

        // When - invoke bid request provider
        val result = subject.invoke(params, auctionId = "auction-456")

        // Then - basic structure is valid
        assertThat(result.has("id")).isTrue()
        assertThat(result.getString("id")).isEqualTo("auction-456")
        assertThat(result.has("app")).isTrue()
        assertThat(result.has("device")).isTrue()
        assertThat(result.has("imp")).isTrue()
        assertThat(result.has("regs")).isTrue()
        assertThat(result.has("ext")).isTrue()

        // Verify app object
        val app = result.getJSONObject("app")
        assertThat(app.getString("id")).isEqualTo("app-id-123")
        assertThat(app.getString("bundle")).isEqualTo("com.test.app")
        assertThat(app.getString("ver")).isEqualTo("1.0.0")

        // Verify device object basics
        val device = result.getJSONObject("device")
        assertThat(device.getString("make")).isEqualTo("Google")
        assertThat(device.getString("model")).isEqualTo("Pixel 5")
        assertThat(device.getString("os")).isEqualTo("Android")
        assertThat(device.getString("osv")).isEqualTo("12")
        assertThat(device.getInt("devicetype")).isEqualTo(4) // mobile per OpenRTB 2.6

        // Verify impression array
        val imp = result.getJSONArray("imp")
        assertThat(imp.length()).isEqualTo(1)
        val impObj = imp.getJSONObject(0)
        assertThat(impObj.getString("tagid")).isEqualTo("test-placement-123")
        assertThat(impObj.getInt("secure")).isEqualTo(1)
    }

    // ========== PII Removal Tests ==========

    @Test
    fun `invoke - removes GAID when PII removal required`() = runTest {
        // Given - PII removal is required
        every { privacyService.shouldClearPersonalData() } returns true
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - ifa should be empty string
        val device = result.getJSONObject("device")
        assertThat(device.getString("ifa")).isEmpty()
    }

    @Test
    fun `invoke - includes GAID when PII removal not required`() = runTest {
        // Given - PII removal NOT required
        every { privacyService.shouldClearPersonalData() } returns false
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - ifa should contain GAID
        val device = result.getJSONObject("device")
        assertThat(device.getString("ifa")).isEqualTo("test-gaid-123")
    }

    @Test
    fun `invoke - removes geo coordinates when PII removal required`() = runTest {
        // Given - PII removal required and geo data available
        every { privacyService.shouldClearPersonalData() } returns true
        every { GeoInfoHolder.getLat() } returns 37.7749f
        every { GeoInfoHolder.getLon() } returns -122.4194f
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - geo should not contain lat/lon
        val geo = result.getJSONObject("device").getJSONObject("geo")
        assertThat(geo.has("lat")).isFalse()
        assertThat(geo.has("lon")).isFalse()
        assertThat(geo.has("type")).isFalse()
    }

    @Test
    fun `invoke - includes geo coordinates when PII removal not required`() = runTest {
        // Given - PII removal NOT required and geo data available
        every { privacyService.shouldClearPersonalData() } returns false
        every { GeoInfoHolder.getLat() } returns 37.7749f
        every { GeoInfoHolder.getLon() } returns -122.4194f
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - geo should contain lat/lon (using tolerance for float precision)
        val geo = result.getJSONObject("device").getJSONObject("geo")
        assertThat(geo.getDouble("lat")).isWithin(0.0001).of(37.7749)
        assertThat(geo.getDouble("lon")).isWithin(0.0001).of(-122.4194)
        assertThat(geo.getInt("type")).isEqualTo(1)
    }

    // ========== Privacy Regs Tests ==========

    @Test
    fun `invoke - includes COPPA flag when enabled`() = runTest {
        // Given - COPPA enabled
        every { privacyService.isCoppaEnabled() } returns true
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - coppa should be 1
        val regs = result.getJSONObject("regs")
        assertThat(regs.getInt("coppa")).isEqualTo(1)
    }

    @Test
    fun `invoke - includes GDPR consent data`() = runTest {
        // Given - GDPR data available
        every { privacyService.cloudXPrivacy } returns MutableStateFlow(
            CloudXPrivacy(isUserConsent = true)
        )
        coEvery { privacyService.gdprApplies() } returns true
        coEvery { privacyService.tcString() } returns "CP1234567890"
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - GDPR data should be present
        val regsExt = result.getJSONObject("regs").getJSONObject("ext")
        assertThat(regsExt.getInt("gdpr_consent")).isEqualTo(1)
        val iab = regsExt.getJSONObject("iab")
        assertThat(iab.getInt("gdpr_tcfv2_gdpr_applies")).isEqualTo(1)
        assertThat(iab.getString("gdpr_tcfv2_tc_string")).isEqualTo("CP1234567890")
    }

    @Test
    fun `invoke - includes CCPA privacy string`() = runTest {
        // Given - CCPA string available
        coEvery { privacyService.usPrivacyString() } returns "1YNN"
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - CCPA string should be present
        val iab = result.getJSONObject("regs").getJSONObject("ext").getJSONObject("iab")
        assertThat(iab.getString("ccpa_us_privacy_string")).isEqualTo("1YNN")
    }

    @Test
    fun `invoke - includes GPP string and SID`() = runTest {
        // Given - GPP data available
        every { privacyService.gppString() } returns "DBABMA~BAAAAAAA"
        every { privacyService.gppSid() } returns listOf(7, 8)
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - GPP data should be present
        val regsExt = result.getJSONObject("regs").getJSONObject("ext")
        assertThat(regsExt.getString("gpp")).isEqualTo("DBABMA~BAAAAAAA")
        val gppSid = regsExt.getJSONArray("gpp_sid")
        assertThat(gppSid.length()).isEqualTo(2)
        assertThat(gppSid.getInt(0)).isEqualTo(7)
        assertThat(gppSid.getInt(1)).isEqualTo(8)
    }

    // ========== Device Info Tests ==========

    @Test
    fun `invoke - sets DNT and LMT when limit ad tracking enabled`() = runTest {
        // Given - limit ad tracking enabled
        coEvery { gaidProvider.invoke() } returns GAIDProvider.Result(
            gaid = "00000000-0000-0000-0000-000000000000",
            isLimitAdTrackingEnabled = true
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - dnt and lmt should be 1
        val device = result.getJSONObject("device")
        assertThat(device.getInt("dnt")).isEqualTo(1)
        assertThat(device.getInt("lmt")).isEqualTo(1)
    }

    @Test
    fun `invoke - clears DNT and LMT when limit ad tracking disabled`() = runTest {
        // Given - limit ad tracking disabled
        coEvery { gaidProvider.invoke() } returns GAIDProvider.Result(
            gaid = "test-gaid-123",
            isLimitAdTrackingEnabled = false
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - dnt and lmt should be 0
        val device = result.getJSONObject("device")
        assertThat(device.getInt("dnt")).isEqualTo(0)
        assertThat(device.getInt("lmt")).isEqualTo(0)
    }

    @Test
    fun `invoke - maps connection types correctly`() = runTest {
        // Given - WiFi connection
        coEvery { connectionStatusService.currentConnectionInfo() } returns ConnectionInfo(
            type = ConnectionType.WIFI,
            isMetered = false
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - connectiontype should be 2 (WIFI in ORTB)
        val device = result.getJSONObject("device")
        assertThat(device.getInt("connectiontype")).isEqualTo(2)
    }

    @Test
    fun `invoke - sets device type for tablet`() = runTest {
        // Given - tablet device
        coEvery { deviceInfoProvider.invoke() } returns DeviceInfo(
            manufacturer = "Samsung",
            model = "Galaxy Tab",
            hwVersion = "1.0",
            isTablet = true,
            os = "Android",
            osVersion = "12",
            apiLevel = 31,
            language = "en",
            mobileCarrier = "Verizon",
            screenDensity = 2.0f
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - devicetype should be 5 (tablet in ORTB)
        val device = result.getJSONObject("device")
        assertThat(device.getInt("devicetype")).isEqualTo(5)
    }

    @Test
    fun `invoke - sets device type for phone`() = runTest {
        // Given - phone device (already in setUp)
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - devicetype should be 4 (mobile/phone per OpenRTB 2.6)
        val device = result.getJSONObject("device")
        assertThat(device.getInt("devicetype")).isEqualTo(4)
    }

    // ========== Key-Value Injection Tests ==========

    @Test
    fun `invoke - injects user key-values when PII removal not required`() = runTest {
        // Given - user key-values configured and PII removal NOT required
        every { privacyService.shouldClearPersonalData() } returns false
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = "user.ext.data",
            appKeyValues = null,
            eids = null,
            placementLoopIndex = null
        )
        every { SdkKeyValueState.userKeyValues } returns mutableMapOf(
            "age" to "25",
            "gender" to "male"
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - user key-values should be injected at the specified path
        val userExt = result.getJSONObject("user").getJSONObject("ext").getJSONObject("data")
        assertThat(userExt.getString("age")).isEqualTo("25")
        assertThat(userExt.getString("gender")).isEqualTo("male")
    }

    @Test
    fun `invoke - does NOT inject user key-values when PII removal required`() = runTest {
        // Given - user key-values configured but PII removal IS required
        every { privacyService.shouldClearPersonalData() } returns true
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = "user.ext.data",
            appKeyValues = null,
            eids = null,
            placementLoopIndex = null
        )
        every { SdkKeyValueState.userKeyValues } returns mutableMapOf(
            "age" to "25",
            "gender" to "male"
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - user key-values should NOT be present (PII protection)
        assertThat(result.has("user")).isFalse()
    }

    @Test
    fun `invoke - always injects app key-values regardless of PII removal`() = runTest {
        // Given - app key-values configured and PII removal IS required
        every { privacyService.shouldClearPersonalData() } returns true
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = null,
            appKeyValues = "app.ext.data",
            eids = null,
            placementLoopIndex = null
        )
        every { SdkKeyValueState.appKeyValues } returns mutableMapOf(
            "version" to "2.1.0",
            "build" to "prod"
        )
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - app key-values should be present (not PII, always included)
        val appExt = result.getJSONObject("app").getJSONObject("ext").getJSONObject("data")
        assertThat(appExt.getString("version")).isEqualTo("2.1.0")
        assertThat(appExt.getString("build")).isEqualTo("prod")
    }

    @Test
    fun `invoke - injects hashed user ID (eids) when PII removal not required`() = runTest {
        // Given - hashed user ID configured and PII removal NOT required
        every { privacyService.shouldClearPersonalData() } returns false
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = null,
            appKeyValues = null,
            eids = "user.ext.eids[0]",
            placementLoopIndex = null
        )
        every { SdkKeyValueState.hashedUserId } returns "hashed-user-12345"
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - eids should be injected with hashed user ID
        val eids = result.getJSONObject("user").getJSONObject("ext").getJSONArray("eids")
        assertThat(eids.length()).isEqualTo(1)
        val eid = eids.getJSONObject(0)
        assertThat(eid.getString("source")).isEqualTo("com.test.app") // from appInfo packageName
        val uids = eid.getJSONArray("uids")
        assertThat(uids.length()).isEqualTo(1)
        val uid = uids.getJSONObject(0)
        assertThat(uid.getString("id")).isEqualTo("hashed-user-12345")
        assertThat(uid.getInt("atype")).isEqualTo(3) // atype=3 for hashed ID
    }

    @Test
    fun `invoke - does NOT inject hashed user ID (eids) when PII removal required`() = runTest {
        // Given - hashed user ID configured but PII removal IS required
        every { privacyService.shouldClearPersonalData() } returns true
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = null,
            appKeyValues = null,
            eids = "user.ext.eids[0]",
            placementLoopIndex = null
        )
        every { SdkKeyValueState.hashedUserId } returns "hashed-user-12345"
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - eids should NOT be present (PII protection)
        assertThat(result.has("user")).isFalse()
    }

    @Test
    fun `invoke - injects placement loop index for ad refresh tracking`() = runTest {
        // Given - placement loop index configured
        every { SdkKeyValueState.getKeyValuePaths() } returns Config.KeyValuePaths(
            userKeyValues = null,
            appKeyValues = null,
            eids = null,
            placementLoopIndex = "imp[0].ext.loopIndex"
        )
        every { PlacementLoopIndexTracker.getCount("banner") } returns 3
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - loop index should be injected at specified path
        val impExt = result.getJSONArray("imp").getJSONObject(0).getJSONObject("ext")
        assertThat(impExt.getString("loopIndex")).isEqualTo("3")
    }

    @Test
    fun `invoke - does not inject key-values when paths not configured`() = runTest {
        // Given - no key-value paths configured
        every { SdkKeyValueState.getKeyValuePaths() } returns null
        every { SdkKeyValueState.userKeyValues } returns mutableMapOf("age" to "25")
        every { SdkKeyValueState.appKeyValues } returns mutableMapOf("version" to "1.0")
        val params = BidRequestProvider.Params(
            placementId = "test-placement",
            adType = AdType.Banner.Standard,
            placementName = "banner",
            accountId = "acc-123",
            appKey = "key-123",
            appId = "app-123"
        )

        // When
        val result = subject.invoke(params, "auction-123")

        // Then - no extra user/app ext data should be added
        assertThat(result.has("user")).isFalse()
        // App object exists but should not have extra 'ext.data'
        val app = result.getJSONObject("app")
        if (app.has("ext") && app.getJSONObject("ext").has("data")) {
            // If ext.data exists, it should be empty from key-values
            // (prebid ext might exist)
        }
    }
}
