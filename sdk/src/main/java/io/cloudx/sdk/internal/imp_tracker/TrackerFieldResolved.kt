package io.cloudx.sdk.internal.imp_tracker

import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal object TrackingFieldResolver {
    const val SDK_PARAM_RESPONSE_IN_MILLIS = "sdk.responseTimeMillis"

    private const val SDK_PARAM_SDK_VERSION = "sdk.releaseVersion"
    private const val SDK_PARAM_DEVICE_TYPE = "sdk.deviceType"
    private const val SDK_PARAM_SESSION_ID = "sdk.sessionId"
    private const val CONFIG_PARAM_AB_TEST_GROUP = "config.testGroupName"

    private const val BID_REQUEST_PARAM_LOOP_INDEX = "bidRequest.loopIndex"
    private const val BID_REQUEST_PARAM_IFA = "bidRequest.device.ifa"

    private var tracking: List<String>? = null
    private val requestDataMap = ConcurrentHashMap<String, JSONObject>()
    private val responseDataMap = ConcurrentHashMap<String, JSONObject>()
    private val loadedBidMap = ConcurrentHashMap<String, String>()
    private var configDataMap: JSONObject? = null
    private val sdkMap = ConcurrentHashMap<String, MutableMap<String, String>>()
    private var auctionedLoopIndex = ConcurrentHashMap<String, Int>()

    private var sessionId: String? = null
    private var sdkVersion: String? = null
    private var deviceType: String? = null
    private var abTestGroup: String? = null
    private var hashedGeoIp: String? = null

    private var accountId: String? = null

    fun setConfig(config: Config) {
        accountId = config.accountId
        tracking = config.trackers
        configDataMap = config.rawJson
    }

    fun setSessionConstData(
        sessionId: String,
        sdkVersion: String,
        deviceType: String,
        abTestGroup: String
    ) {
        TrackingFieldResolver.sessionId = sessionId
        TrackingFieldResolver.sdkVersion = sdkVersion
        TrackingFieldResolver.deviceType = deviceType
        TrackingFieldResolver.abTestGroup = abTestGroup
    }

    fun setRequestData(auctionId: String, json: JSONObject) {
        requestDataMap[auctionId] = json
    }

    fun setResponseData(auctionId: String, json: JSONObject) {
        responseDataMap[auctionId] = json
    }

    fun setSdkParam(auctionId: String, key: String, value: String) {
        val params = sdkMap.getOrPut(auctionId) { mutableMapOf() }
        params[key] = value
    }

    fun saveLoadedBid(auctionId: String, bidId: String) {
        loadedBidMap[auctionId] = bidId
    }

    fun setLoopIndex(auctionId: String, loopIndex: Int) {
        auctionedLoopIndex[auctionId] = loopIndex
    }

    fun getAccountId(): String? {
        return accountId
    }

    fun buildPayload(auctionId: String): String? {
        val trackingList = tracking ?: return null

        val values = trackingList.map { field ->
            resolveField(auctionId, field)?.toString().orEmpty()
        }

        val payload = values.joinToString(";")
        println("hop: payload = $payload, for CLICK event, auctionI-clickCount")
        return payload
    }

    fun setHashedGeoIp(hashedGeoIp: String) {
        this.hashedGeoIp = hashedGeoIp
    }

    fun clear() {
        requestDataMap.clear()
        responseDataMap.clear()
        loadedBidMap.clear()
        sdkMap.clear()
        auctionedLoopIndex.clear()
    }

    private fun Any?.resolveNestedField(path: String): Any? {
        var current: Any? = this

        for (segment in path.split('.')) {

            val filterMatch = Regex("""^(\w+)\[(\w+)=(.+)]$""").find(segment)
            if (filterMatch != null) {
                val arrayName = filterMatch.groupValues[1]
                val filterKey = filterMatch.groupValues[2]
                val filterValue = filterMatch.groupValues[3]

                val arr = (current as? JSONObject)?.optJSONArray(arrayName) ?: return null

                current = (0 until arr.length())
                    .map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString(filterKey) == filterValue }
                    ?: return null

                continue
            }

            while (current is JSONArray) {
                current = if (current.length() > 0) current.opt(0) else return null
            }

            current = (current as? JSONObject)?.opt(segment) ?: return null
        }

        if (current is JSONArray) {
            current = if (current.length() > 0) current.opt(0) else null
        }

        return current
    }


    private fun resolveField(auctionId: String, field: String): Any? {
        // placeholder‐expander
        val placeholderRegex = Regex("""\$\{([^}]+)\}""")
        fun expandTemplate(template: String): String =
            placeholderRegex.replace(template) { m ->
                val innerPath = m.groupValues[1]
                resolveField(auctionId, innerPath)?.toString().orEmpty()
            }

        return when {
            // —— BID fields ——
            field.startsWith("bid.") -> {
                val bidId = loadedBidMap[auctionId] ?: return null
                val seatbid = responseDataMap[auctionId]?.optJSONArray("seatbid") ?: return null

                // find winning bid object
                val bidObj = sequence {
                    for (i in 0 until seatbid.length()) {
                        val seat = seatbid.getJSONObject(i)
                        val bids = seat.optJSONArray("bid") ?: continue
                        for (j in 0 until bids.length())
                            yield(bids.getJSONObject(j))
                    }
                }.firstOrNull { it.optString("id") == bidId } ?: return null

                // strip prefix, expand placeholders, then resolve deep path
                val rawTemplate = field.removePrefix("bid.")
                val expandedPath = expandTemplate(rawTemplate)
                bidObj.resolveNestedField(expandedPath)
            }

            // —— BID REQUEST fields ——
            field.startsWith("bidRequest.") -> {
                if (field == BID_REQUEST_PARAM_LOOP_INDEX) {
                    return auctionedLoopIndex[auctionId]
                }
                if (field == BID_REQUEST_PARAM_IFA) { // TODO: CX-919 Temporary Hardcoded Solution
                    if (PrivacyService().shouldClearPersonalData()) {
                        return sessionId
                    }
                    val isLimitedAdTrackingEnabled = requestDataMap[auctionId]?.optJSONObject("device")?.optInt("dnt") == 1
                    return if (isLimitedAdTrackingEnabled) {
                        val hashedUserId = SdkKeyValueState.hashedUserId.orEmpty()
                        val hasUserHashedId = hashedUserId.isBlank().not()
                        if (hasUserHashedId) {
                            hashedUserId
                        } else {
                            hashedGeoIp
                        }
                    } else {
                        // ifa
                        requestDataMap[auctionId]?.optJSONObject("device")?.optString("ifa")
                    }
                }
                val json = requestDataMap[auctionId] ?: return null
                val rawTemplate = field.removePrefix("bidRequest.")
                val expandedPath = expandTemplate(rawTemplate)
                json.resolveNestedField(expandedPath)
            }

            // —— CONFIG fields ——
            field.startsWith("config.") -> {
                if (field == CONFIG_PARAM_AB_TEST_GROUP) {
                    return abTestGroup
                }
                val rawTemplate = field.removePrefix("config.")
                val expandedPath = expandTemplate(rawTemplate)
                configDataMap?.resolveNestedField(expandedPath)
            }

            // —— SDK fields ——
            field.startsWith("sdk.") -> {
                return when (field) {
                    SDK_PARAM_SESSION_ID  -> sessionId
                    SDK_PARAM_SDK_VERSION -> sdkVersion
                    SDK_PARAM_DEVICE_TYPE -> deviceType
                    else                  -> sdkMap[auctionId]?.get(field)
                }
            }
            // —— RESPONSE fields ——
            field.startsWith("bidResponse.") -> {
                val json = responseDataMap[auctionId] ?: return null
                val rawTemplate = field.removePrefix("bidResponse.")
                val expandedPath = expandTemplate(rawTemplate)
                json.resolveNestedField(expandedPath)
            }

            else -> null
        }
    }
}