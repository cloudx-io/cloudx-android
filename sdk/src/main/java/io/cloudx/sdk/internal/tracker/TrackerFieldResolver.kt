package io.cloudx.sdk.internal.tracker

import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.privacy.PrivacyService
import io.cloudx.sdk.internal.state.SdkKeyValueState
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal object TrackingFieldResolver {
    const val SDK_PARAM_RESPONSE_IN_MILLIS = "sdk.responseTimeMillis"
    private const val SDK_PARAM_APP_BUNDLE = "sdk.app.bundle"
    private const val SDK_PARAM_SDK_VERSION = "sdk.releaseVersion"
    private const val SDK_PARAM_DEVICE_TYPE = "sdk.deviceType"
    private const val SDK_PARAM_SESSION_ID = "sdk.sessionId"
    private const val SDK_PARAM_ABTEST_GROUP = "sdk.testGroupName"
    private const val SDK_PARAM_LOOP_INDEX = "sdk.loopIndex"
    private const val SDK_PARAM_IFA = "sdk.ifa"

    private var tracking: List<String>? = null
    private val requestDataMap = ConcurrentHashMap<String, JSONObject>()
    private val responseDataMap = ConcurrentHashMap<String, JSONObject>()
    private var configDataMap: JSONObject? = null
    private val sdkMap = ConcurrentHashMap<String, MutableMap<String, String>>()
    private var auctionedLoopIndex = ConcurrentHashMap<String, Int>()

    private var sessionId: String? = null
    private var sdkVersion: String? = null
    private var deviceType: String? = null
    private var abTestGroup: String? = null
    private var appBundle: String? = null
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
        abTestGroup: String,
        appBundle: String
    ) {
        TrackingFieldResolver.sessionId = sessionId
        TrackingFieldResolver.sdkVersion = sdkVersion
        TrackingFieldResolver.deviceType = deviceType
        TrackingFieldResolver.abTestGroup = abTestGroup
        TrackingFieldResolver.appBundle = appBundle
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

    fun setLoopIndex(auctionId: String, loopIndex: Int) {
        auctionedLoopIndex[auctionId] = loopIndex
    }

    fun getAccountId(): String? {
        return accountId
    }

    fun buildPayload(auctionId: String, bidId: String? = null): String? {
        val trackingList = tracking ?: return null

        val values = trackingList.map { field ->
            resolveField(auctionId, field, bidId)?.toString().orEmpty()
        }

        return values.joinToString(";")
    }

    fun setHashedGeoIp(hashedGeoIp: String) {
        this.hashedGeoIp = hashedGeoIp
    }

    fun clear() {
        requestDataMap.clear()
        responseDataMap.clear()
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

    fun resolveField(auctionId: String, field: String, bidId: String? = null): Any? {
        // placeholder‐expander
        val placeholderRegex = Regex("""\$\{([^}]+)\}""")
        fun expandTemplate(template: String): String =
            placeholderRegex.replace(template) { m ->
                val innerPath = m.groupValues[1]
                resolveField(auctionId, innerPath, bidId)?.toString().orEmpty()
            }

        return when {
            // —— BID fields ——
            field.startsWith("bid.") -> {
                val targetBidId = bidId ?: return null // bidId is now required for bid fields
                val seatbid = responseDataMap[auctionId]?.optJSONArray("seatbid") ?: return null

                // find specific bid object by bidId
                val bidObj = sequence {
                    for (i in 0 until seatbid.length()) {
                        val seat = seatbid.getJSONObject(i)
                        val bids = seat.optJSONArray("bid") ?: continue
                        for (j in 0 until bids.length())
                            yield(bids.getJSONObject(j))
                    }
                }.firstOrNull { it.optString("id") == targetBidId } ?: return null

                // strip prefix, expand placeholders, then resolve deep path
                val rawTemplate = field.removePrefix("bid.")
                val expandedPath = expandTemplate(rawTemplate)
                bidObj.resolveNestedField(expandedPath)
            }

            // —— BID REQUEST fields ——
            field.startsWith("bidRequest.") -> {
                val json = requestDataMap[auctionId] ?: return null
                val rawTemplate = field.removePrefix("bidRequest.")
                val expandedPath = expandTemplate(rawTemplate)
                json.resolveNestedField(expandedPath)
            }

            // —— CONFIG fields ——
            field.startsWith("config.") -> {
                val rawTemplate = field.removePrefix("config.")
                val expandedPath = expandTemplate(rawTemplate)
                configDataMap?.resolveNestedField(expandedPath)
            }

            // —— SDK fields ——
            field.startsWith("sdk.") -> {
                return when (field) {
                    SDK_PARAM_SESSION_ID -> sessionId
                    SDK_PARAM_APP_BUNDLE -> appBundle
                    SDK_PARAM_SDK_VERSION -> sdkVersion
                    SDK_PARAM_DEVICE_TYPE -> deviceType
                    SDK_PARAM_LOOP_INDEX -> auctionedLoopIndex[auctionId]?.toString()
                    SDK_PARAM_IFA -> handleIfaField(auctionId)
                    SDK_PARAM_ABTEST_GROUP -> abTestGroup
                    else -> sdkMap[auctionId]?.get(field)
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

    private fun handleIfaField(auctionId: String): String? {
        if (PrivacyService().shouldClearPersonalData()) {
            return sessionId
        }
        val isLimitedAdTrackingEnabled =
            requestDataMap[auctionId]?.optJSONObject("device")?.optInt("dnt") == 1
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
}
