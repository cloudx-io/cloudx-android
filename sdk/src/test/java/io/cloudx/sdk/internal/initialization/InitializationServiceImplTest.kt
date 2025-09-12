package io.cloudx.sdk.internal.initialization

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import io.cloudx.sdk.Result
import io.cloudx.sdk.RoboMockkTest
import io.cloudx.sdk.internal.crash.CrashReportingService
import io.cloudx.sdk.internal.deviceinfo.DeviceInfoProvider
import io.cloudx.sdk.internal.geo.GeoApi
import io.cloudx.sdk.internal.imp_tracker.EventTracker
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsTracker
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.mocks.MockAdapterFactoryResolver
import io.cloudx.sdk.mocks.MockConfigAPIWithPredefinedConfig
import io.cloudx.sdk.mocks.MockConfigRequestProviderWithArbitraryValues
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InitializationServiceImplTest : RoboMockkTest() {

    // TODO: More tests.

    @Test
    fun serviceInitializedWhenConfigAPIRequestSuccessful() = runTest {
        // Chainable editor for prefs.edit() calls
        val editor = mockk<SharedPreferences.Editor> {
            every { putString(any(), any()) } returns this
            every { remove(any()) } returns this
            every { apply() } returns Unit
        }

        // Prefs: return null for pending_crash + support edit()
        val prefs = mockk<SharedPreferences> {
            every { getString("pending_crash", any()) } returns null
            every { edit() } returns editor
        }

        // App context:
        // 1) answers getSharedPreferences("cloudx_crash_store", MODE_*)
        // 2) returns itself for applicationContext (important!)
        val appCtx = mockk<Context>(relaxed = false) {
            every { getSharedPreferences("cloudx_crash_store", any()) } returns prefs
            every { applicationContext } returns this  // <- KEY LINE
        }

        // If you still want an Activity mock, make it hand back appCtx:
        val activity = mockk<Activity> {
            every { applicationContext } returns appCtx
        }

        val svc = InitializationServiceImpl(
            configApi = MockConfigAPIWithPredefinedConfig(),
            provideConfigRequest = MockConfigRequestProviderWithArbitraryValues(),
            adapterResolver = MockAdapterFactoryResolver(),
            privacyService = PrivacyService(),
            _metricsTracker = MetricsTracker(),
            provideDeviceInfo = DeviceInfoProvider(),
            eventTracker = EventTracker(),
            geoApi = GeoApi(),
            context = activity.applicationContext, // or just appCtx
            crashReportingService = CrashReportingService()
        )

        val result = svc.initialize("random_app_key")

        assert(result is Result.Success) { "expected Success result, got $result" }
    }
}