package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.RoboMockkTest
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.httpclient.UserAgentProvider
import io.cloudx.sdk.internal.screen.ScreenService
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.fake.FakeAppInfoProvider
import io.cloudx.sdk.fake.FakeScreenService
import io.cloudx.sdk.fake.FakeUserAgentProvider
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test
import java.util.UUID

class BidApiTest: RoboMockkTest() {
    private lateinit var provideBidRequest: BidRequestProvider
    private lateinit var bidApi: BidApi

    override fun before() {
        super.before()

        mockkStatic(::AppInfoProvider).also {
            every {
                AppInfoProvider()
            } returns FakeAppInfoProvider
        }

        mockkStatic(::ScreenService).also {
            every {
                ScreenService(any())
            } returns FakeScreenService
        }

        mockkStatic(::UserAgentProvider).also {
            every {
                UserAgentProvider()
            } returns FakeUserAgentProvider
        }

        provideBidRequest = BidRequestProvider(mapOf())
        bidApi = BidApi(
            endpointUrl = "https://ads.cloudx.io/openrtb2/auction",
            10000L
        )
    }

    @Test
    @Ignore("TODO: Robolectric getApplicationContext mocking")
    fun endpointRequestResultSuccess() = runTest {
        val params = BidRequestProvider.Params(
            placementId = "mrec-300x250-538295628539",
            adType = AdType.Banner.MREC,
            placementName = "",
            accountId = "",
            appKey = "",
            appId = ""
        )

        val auctionId = UUID.randomUUID().toStr()
        val bidParams = provideBidRequest.invoke(params, auctionId)
        val result = bidApi.invoke("", bidParams)

        assert(result is Result.Success) {
            "Expected successful endpoint response, actual: ${(result as Result.Failure).value.effectiveMessage}"
        }
    }
}