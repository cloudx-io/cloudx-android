package io.cloudx.sdk.internal.config

import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.Result
import io.cloudx.sdk.RoboMockkTest
import io.cloudx.sdk.internal.appinfo.AppInfoProvider
import io.cloudx.sdk.internal.httpclient.UserAgentProvider
import io.cloudx.sdk.mocks.MockAppInfoProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ConfigApiTest : RoboMockkTest() {

    private lateinit var provideConfigRequest: ConfigRequestProvider

    override fun before() {
        super.before()

        mockkStatic(::AppInfoProvider).also {
            every {
                AppInfoProvider()
            } returns MockAppInfoProvider
        }

        provideConfigRequest = ConfigRequestProvider()
    }

    @Test
    fun endpointRequestResultSuccess() = runTest {
        val appKey = "1c3589a1-rgto-4573-zdae-644c65074537"

        val configApi = ConfigApi(BuildConfig.CLOUDX_ENDPOINT_CONFIG)
        val result = configApi.invoke(appKey, provideConfigRequest())

        assert(result is Result.Success) {
            "Expected successful endpoint response, actual: ${(result as Result.Failure).value.effectiveMessage}"
        }
    }

    @Test
    fun retryOnStatusErrors() = runTest {
        val appKey = "1c3589a1-rgto-4573-zdae-644c65074537"

        val mockEngine = MockEngine { request ->
            respondError(HttpStatusCode.InternalServerError)
        }

        val configApi = ConfigApiImpl(
            endpointUrl = BuildConfig.CLOUDX_ENDPOINT_CONFIG,
            timeoutMillis = 5000,
            httpClient = HttpClient(mockEngine) {
                install(UserAgent) {
                    agent = UserAgentProvider()()
                }
                install(HttpTimeout)
                install(HttpRequestRetry)
            }
        )

        val result = configApi.invoke(appKey, provideConfigRequest())

        assert(result is Result.Failure) {
            "Expected failure result for server error"
        }
    }

    @Test
    fun retryOnExceptions() = runTest {
        val appKey = "1c3589a1-rgto-4573-zdae-644c65074537"

        val mockEngine = MockEngine { request ->
            throw UnresolvedAddressException()
        }

        val configApi = ConfigApiImpl(
            endpointUrl = BuildConfig.CLOUDX_ENDPOINT_CONFIG,
            timeoutMillis = 5000,
            httpClient = HttpClient(mockEngine) {
                install(UserAgent) {
                    agent = UserAgentProvider()()
                }
                install(HttpTimeout)
                install(HttpRequestRetry)
            }
        )

        val result = configApi.invoke(appKey, provideConfigRequest())

        assert(result is Result.Failure) {
            "Expected failure result for network exception"
        }
    }

    @Test
    fun retryOnTooManyRequests() = runTest {
        val appKey = "1c3589a1-rgto-4573-zdae-644c65074537"

        val mockEngine = MockEngine { request ->
            respondError(HttpStatusCode.TooManyRequests)
        }

        val configApi = ConfigApiImpl(
            endpointUrl = BuildConfig.CLOUDX_ENDPOINT_CONFIG,
            timeoutMillis = 5000,
            httpClient = HttpClient(mockEngine) {
                install(UserAgent) {
                    agent = UserAgentProvider()()
                }
                install(HttpTimeout)
                install(HttpRequestRetry)
            }
        )

        val result = configApi.invoke(appKey, provideConfigRequest())
        
        assert(result is Result.Failure) {
            "Expected failure result for 429 status"
        }
    }
}