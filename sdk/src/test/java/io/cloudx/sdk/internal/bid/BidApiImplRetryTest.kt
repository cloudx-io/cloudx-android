package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.Result
import io.cloudx.sdk.RoboMockkTest
import io.cloudx.sdk.internal.CLXErrorCode
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.HEADER_CLOUDX_STATUS
import io.cloudx.sdk.internal.STATUS_ADS_DISABLED
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidApiImplRetryTest : RoboMockkTest() {

    override fun before() {
        super.before()
        CloudXLogger.isEnabled = false
    }

    private fun httpClientWith(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(HttpTimeout)
        install(HttpRequestRetry) // Let postJsonWithRetry configure retry behavior
    }

    private fun bidApiWith(engine: MockEngine): BidApiImpl =
        BidApiImpl(
            endpointUrl = "http://test/endpoint",
            timeoutMillis = 5_000,
            httpClient = httpClientWith(engine)
        )

    private fun minimalBidResponseJson(): String =
        // Minimal valid OpenRTB-like payload with one bid
        """
        {
          "id": "auction-1",
          "seatbid": [
            {"bid": [
              {
                "id": "b1",
                "adm": "<html></html>",
                "price": 1.0,
                "ext": {
                  "prebid": { "meta": { "adaptercode": "meta" } },
                  "cloudx": { "rank": 1 }
                }
              }
            ]}
          ]
        }
        """.trimIndent()

    @Test
    fun `200 OK returns success without retry`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            respond(
                content = minimalBidResponseJson(), 
                status = HttpStatusCode.OK, 
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            )
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        
        // Debug output
        if (res is Result.Failure) {
            println("FAILURE: ${res.value.code} - ${res.value.message}")
            res.value.cause?.printStackTrace()
        }
        
        assertTrue("Expected Success, got: $res", res is Result.Success)
        assertEquals("Expected 1 call, got: $calls", 1, calls)
    }

    @Test
    fun `204 No Content without ADS_DISABLED NO_FILL no retry`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", status = HttpStatusCode.NoContent)
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Failure)
        res as Result.Failure
        assertEquals(CLXErrorCode.NO_FILL, res.value.code)
        assertEquals(1, calls)
    }

    @Test
    fun `204 No Content with ADS_DISABLED ADS_DISABLED no retry`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = Headers.build { append(HEADER_CLOUDX_STATUS, STATUS_ADS_DISABLED) }
            )
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Failure)
        res as Result.Failure
        assertEquals(CLXErrorCode.ADS_DISABLED, res.value.code)
        assertEquals(1, calls)
    }

    @Test
    fun `429 TooManyRequests retried once then success`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (calls == 1) {
                respond("rate limited", status = HttpStatusCode.TooManyRequests)
            } else {
                respond(minimalBidResponseJson(), status = HttpStatusCode.OK, headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                })
            }
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Success)
        assertEquals(2, calls)
    }

    @Test
    fun `5xx server error retried once then failure`() = runTest {
        // MVP spec: "5xx Server Errors: Retry once after 1-second delay"
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            respond("oops", status = HttpStatusCode.InternalServerError)
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Failure)
        res as Result.Failure
        assertEquals(CLXErrorCode.SERVER_ERROR, res.value.code)
        assertEquals(2, calls) // MVP compliance: initial call + 1 retry = 2 total calls
    }

    @Test
    fun `5xx server error retried once then success`() = runTest {
        // MVP spec: "5xx Server Errors: Retry once after 1-second delay"
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (calls == 1) respond("oops", status = HttpStatusCode.InternalServerError)
            else respond(minimalBidResponseJson(), status = HttpStatusCode.OK, headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            })
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue("Expected Success after retry, got: $res", res is Result.Success)
        assertEquals(2, calls) // MVP compliance: initial call + 1 retry = 2 total calls
    }

    @Test
    fun `4xx client error (not 429) is permanent failure, no retry`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            respond("bad request", status = HttpStatusCode.BadRequest)
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Failure)
        res as Result.Failure
        assertEquals(CLXErrorCode.CLIENT_ERROR, res.value.code)
        assertEquals(1, calls)
    }

    @Test
    fun `network error retried once then success`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (calls == 1) throw java.io.IOException("boom")
            else respond(minimalBidResponseJson(), status = HttpStatusCode.OK, headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            })
        }
        val api = bidApiWith(engine)
        val res = api.invoke("appKey", org.json.JSONObject("{}"))
        assertTrue(res is Result.Success)
        assertEquals(2, calls)
    }
}

