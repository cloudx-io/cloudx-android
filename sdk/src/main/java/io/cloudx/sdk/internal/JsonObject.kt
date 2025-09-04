package io.cloudx.sdk.internal

import org.json.JSONObject

internal fun JSONObject.toStringPairMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()

    val keys = keys()
    for (key in keys) {
        runCatching { getString(key) }.getOrNull()?.let {
            map[key] = it
        } ?: CloudXLogger.e(message = "failed to parse value as string for key: $key")
    }

    return map
}