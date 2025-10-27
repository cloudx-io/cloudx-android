package io.cloudx.sdk.internal.config

import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.toAdNetwork
import io.cloudx.sdk.internal.tracker.ErrorReportingService
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.toBundle
import io.cloudx.sdk.toFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun jsonToConfig(json: String): Result<Config, CloudXError> =
    withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(json)

            val auctionEndpoint = root.toEndpointConfig("auctionEndpointURL")
            val cdpEndpoint = root.toEndpointConfig("cdpEndpointURL")

            Result.Success(
                Config(
                    appId = root.getString("appID"),
                    precacheSize = root.getInt("preCacheSize"),
                    auctionEndpointUrl = auctionEndpoint,
                    cdpEndpointUrl = cdpEndpoint,
                    trackingEndpointUrl = root.optString("impressionTrackerURL", null),
                    winLossNotificationUrl = root.optString("winLossNotificationURL", null),
                    bidders = root.getJSONArray("bidders").toBidders(),
                    placements = root.getJSONArray("placements").toPlacements(),
                    geoDataEndpointUrl = root.optString("geoDataEndpointURL", null),
                    sessionId = root.getString("sessionID"),
                    organizationId = root.optString("organizationID", null),
                    accountId = root.optString("accountID", null),
                    appKeyOverride = root.optString("appKeyOverride", null),
                    trackers = root.optJSONArray("tracking")?.toTrackers(),
                    winLossNotificationPayloadConfig = root.optJSONObject("winLossNotificationPayloadConfig")?.toStringMap() ?: emptyMap(),
                    geoHeaders = root.optJSONArray("geoHeaders")?.toGeoHeaders(),
                    keyValuePaths = root.optJSONObject("keyValuePaths")?.let { kvp ->
                        Config.KeyValuePaths(
                            userKeyValues = kvp.optString("userKeyValues", null),
                            appKeyValues = kvp.optString("appKeyValues", null),
                            eids = kvp.optString("eids", null),
                            placementLoopIndex = kvp.optString("placementLoopIndex", null)
                        )
                    },
                    metrics = root.optJSONObject("metrics")?.toMetricsConfig(),
                    rawJson = root
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CXLogger.e(component = "jsonToConfig", message = "Failed to parse config JSON", throwable = e)
            ErrorReportingService().sendErrorEvent(
                errorMessage = "Config JSON parsing failed: ${e.message}",
                errorDetails = e.stackTraceToString()
            )
            CloudXErrorCode.INVALID_RESPONSE.toFailure(cause = e)
        }
    }

private fun JSONArray.toBidders(): Map<AdNetwork, Config.Bidder> {
    val bidders = mutableMapOf<AdNetwork, Config.Bidder>()
    val length = length()

    for (i in 0 until length) {
        val bidder = getJSONObject(i)

        val adNetwork = bidder.getString("networkName").toAdNetwork()

        bidders[adNetwork] = Config.Bidder(
            adNetwork = adNetwork,
            initData = bidder.getJSONObject("initData").toBundle()
        )
    }

    return bidders
}

private fun JSONArray.toTrackers(): List<String> {
    val params = mutableListOf<String>()
    val length = length()

    for (i in 0 until length) {
        val param = getString(i)
        params.add(param)
    }
    return params
}

private fun JSONArray.toGeoHeaders(): List<Config.GeoHeader> {
    val headers = mutableListOf<Config.GeoHeader>()
    for (i in 0 until length()) {
        val obj = getJSONObject(i)
        val source = obj.optString("source")
        val target = obj.optString("target")
        if (source.isNotEmpty() && target.isNotEmpty()) {
            headers.add(Config.GeoHeader(source, target))
        }
    }
    return headers
}

private fun JSONObject.toMetricsConfig(): Config.MetricsConfig {
    return Config.MetricsConfig(
        sendIntervalSeconds = this.optLong("send_interval_seconds", 60),
        sdkApiCallsEnabled = if (has("sdk_api_calls.enabled")) optBoolean("sdk_api_calls.enabled") else null,
        networkCallsEnabled = if (has("network_calls.enabled")) optBoolean("network_calls.enabled") else null,
        networkCallsBidReqEnabled = if (has("network_calls.bid_req.enabled")) optBoolean("network_calls.bid_req.enabled") else null,
        networkCallsInitSdkReqEnabled = if (has("network_calls.init_sdk_req.enabled")) optBoolean("network_calls.init_sdk_req.enabled") else null,
        networkCallsGeoReqEnabled = if (has("network_calls.geo_req.enabled")) optBoolean("network_calls.geo_req.enabled") else null
    )
}

private fun JSONArray.toPlacements(): Map<String, Config.Placement> {
    val placements = mutableMapOf<String, Config.Placement>()
    val length = length()

    for (i in 0 until length) {
        val jsonPlacement = getJSONObject(i)

        val id = jsonPlacement.getString("id")
        val name = jsonPlacement.getString("name")
        val bidResponseTimeoutMillis = 10_000
        val adLoadTimeoutMillis = 10_000
        val placementType = jsonPlacement.getString("type")
        val hasCloseButton = jsonPlacement.opt("hasCloseButton") as? Boolean ?: false

        val placement = when (placementType.uppercase()) {

            "BANNER" -> Config.Placement.Banner(
                id,
                name,
                bidResponseTimeoutMillis,
                adLoadTimeoutMillis,
                refreshRateMillis = jsonPlacement.optInt("bannerRefreshRateMs", 30_000),
                hasCloseButton
            )

            "MREC" -> Config.Placement.MREC(
                id,
                name,
                bidResponseTimeoutMillis,
                adLoadTimeoutMillis,
                refreshRateMillis = jsonPlacement.optInt("bannerRefreshRateMs", 30_000),
                hasCloseButton
            )

            "INTERSTITIAL" -> Config.Placement.Interstitial(
                id, name, bidResponseTimeoutMillis, adLoadTimeoutMillis
            )

            "REWARDED" -> Config.Placement.Rewarded(
                id, name, bidResponseTimeoutMillis, adLoadTimeoutMillis
            )

            "NATIVE" -> Config.Placement.Native(
                id,
                name,
                bidResponseTimeoutMillis,
                adLoadTimeoutMillis,
                jsonPlacement.toNativeTemplateType(),
                refreshRateMillis = jsonPlacement.optInt("bannerRefreshRateMs", 900_000),
                hasCloseButton
            )

            else -> {
                CXLogger.w("JSONArray.toPlacements()", "unknown placement type: $placementType")
                null
            }
        }

        if (placement != null) {
            placements[name] = placement
        }
    }

    return placements
}

private fun JSONObject.toNativeTemplateType(): Config.Placement.Native.TemplateType =
    when (val templateString = getString("nativeTemplate")) {
        "small" -> Config.Placement.Native.TemplateType.Small
        "medium" -> Config.Placement.Native.TemplateType.Medium
        else -> Config.Placement.Native.TemplateType.Unknown(templateString)
    }

internal fun JSONObject.toEndpointConfig(field: String): Config.EndpointConfig {
    val raw = opt(field)
    return when (raw) {
        is String -> Config.EndpointConfig(default = raw)
        is JSONObject -> {
            val testArray = raw.optJSONArray("test") ?: JSONArray()
            val test = mutableListOf<Config.EndpointConfig.TestVariant>()

            for (i in 0 until testArray.length()) {
                val obj = testArray.getJSONObject(i)
                val name = obj.getString("name")
                val url = obj.getString("value")
                val ratio = obj.optDouble("ratio", 1.0)
                test += Config.EndpointConfig.TestVariant(name, url, ratio)
            }

            Config.EndpointConfig(
                default = raw.getString("default"),
                test = test
            )
        }

        else -> Config.EndpointConfig(default = "")
    }
}

private fun JSONObject.toStringMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val keys = keys()

    while (keys.hasNext()) {
        val key = keys.next()
        val value = optString(key)
        if (value.isNotEmpty()) {
            map[key] = value
        }
    }

    return map
}