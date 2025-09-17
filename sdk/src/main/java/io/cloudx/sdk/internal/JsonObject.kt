package io.cloudx.sdk.internal

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toBundle(): Bundle {
    val bundle = Bundle()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = opt(key)

        when (value) {
            is String -> {
                bundle.putString(key, value)
            }

            is JSONArray -> {
                val array = Array(value.length()) { i ->
                    value.optString(i)
                }
                bundle.putStringArray(key, array)
            }

            else -> {
                CXLogger.w(
                    "JSONObject.toBundle",
                    "Unsupported type for key '$key': ${value?.javaClass?.name}"
                )
            }
        }
    }
    return bundle
}
