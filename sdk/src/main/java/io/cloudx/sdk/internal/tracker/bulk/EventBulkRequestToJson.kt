package io.cloudx.sdk.internal.tracker.bulk

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal fun List<EventAM>.toJson(): String {
    val itemsArray = JSONArray()

    for (item in this) {
        val itemObj = JSONObject().apply {
            put("eventName", item.eventName)
            put("campaignId", URLEncoder.encode(item.campaignId, "UTF-8"))
            put("eventValue", item.eventValue)
            put("type", item.type)
            put("impression", URLEncoder.encode(item.impression, "UTF-8"))
        }
        itemsArray.put(itemObj)
    }

    return JSONObject().apply {
        put("items", itemsArray)
    }.toString()
}