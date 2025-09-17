package io.cloudx.sdk.internal.config

import io.cloudx.sdk.internal.CXLogger
import kotlin.random.Random

/**
 * The ResolvedEndpoints class picks URLs for the app to use during A/B testing.
 * It uses one random number to choose only one test URL from all endpoints.
 * The test ratios must add up to 1.0, or it uses default URLs. You can add new endpoints
 * by updating the code with their names and settings.
 * If test data is missing or invalid, it falls back to default URLs.
 */

internal object ResolvedEndpoints {
    lateinit var auctionEndpoint: String
    lateinit var cdpEndpoint: String
    lateinit var geoEndpoint: String

    var testGroupName: String = ""

    fun resolveFrom(config: Config, random: Random = Random) {
        // geoApi - No AB test
        geoEndpoint = config.geoDataEndpointUrl ?: ""

        // AB test configs
        CXLogger.d("Endpoints", "================")
        val randomValue = random.nextDouble()
        CXLogger.d("Endpoints", "Generated random value: $randomValue")

        val tests = listOfNotNull(
            config.auctionEndpointUrl.test?.firstOrNull { it.value.isNotEmpty() }?.let {
                TestCase(it, config.auctionEndpointUrl, "auction")
            },
            config.cdpEndpointUrl.test?.firstOrNull { it.value.isNotEmpty() }?.let {
                TestCase(it, config.cdpEndpointUrl, "cdp")
            }
        )

        if (tests.isEmpty()) {
            CXLogger.d("Endpoints", "No valid test variants found, using defaults")
            assignDefaults(config)
            return
        }

//        val totalRatio = tests.sumOf { it.testVariant.ratio }
//        if (totalRatio != 1.0) {
//            CXLogger.debug("Endpoints", "Error: Total ratio ($totalRatio) must equal 1.0")
//            assignDefaults(config)
//            return
//        }

        var cumulativeRatio = 0.0
        var selectedTest: TestCase? = null
        for (test in tests) {
            cumulativeRatio += test.testVariant.ratio
            if (randomValue <= cumulativeRatio) {
                selectedTest = test
                break
            }
        }

        testGroupName = selectedTest?.testVariant?.name ?: ""

        auctionEndpoint = if (selectedTest?.name == "auction") {
            selectedTest.testVariant.value.takeIf { it.isNotEmpty() }
                ?: config.auctionEndpointUrl.default
        } else {
            config.auctionEndpointUrl.default
        }

        cdpEndpoint = if (selectedTest?.name == "cdp") {
            selectedTest.testVariant.value.takeIf { it.isNotEmpty() }
                ?: config.cdpEndpointUrl.default
        } else {
            config.cdpEndpointUrl.default
        }

        logEndpoints()
    }

    fun reset() {
        auctionEndpoint = ""
        cdpEndpoint = ""
        geoEndpoint = ""
    }

    private fun assignDefaults(config: Config) {
        auctionEndpoint = config.auctionEndpointUrl.default
        cdpEndpoint = config.cdpEndpointUrl.default
        logEndpoints()
    }

    private fun logEndpoints() {
        CXLogger.d("Endpoints", "Resolved Endpoints:")
        CXLogger.d(
            "Endpoints",
            "auction: ${auctionEndpoint.take(30)}${if (auctionEndpoint.length > 30) "..." else ""}"
        )
        CXLogger.d(
            "Endpoints",
            "cdp: ${cdpEndpoint.take(30)}${if (cdpEndpoint.length > 30) "..." else ""}"
        )
        CXLogger.d("Endpoints", "================")
    }

    private data class TestCase(
        val testVariant: Config.EndpointConfig.TestVariant,
        val endpointConfig: Config.EndpointConfig,
        val name: String
    )
}