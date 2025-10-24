package io.cloudx.sdk.internal.config

import org.json.JSONObject

internal fun ConfigRequest.toJson(): String = JSONObject().apply {
    put("bundle", bundle)
    put("os", os)
    put("osVersion", osVersion)
    put("model", deviceModel)
    put("vendor", deviceManufacturer)
    put("ifa", gaid)
    put("sdkVersion", sdkVersion)
    put("dnt", dnt)
}.toString()