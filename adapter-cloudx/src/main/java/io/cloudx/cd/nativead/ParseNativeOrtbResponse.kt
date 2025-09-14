package io.cloudx.cd.nativead

import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun parseNativeOrtbResponse(
    nativeOrtbString: String
): Result<NativeOrtbResponse, String> = withContext(
    Dispatchers.IO
) {
    try {
        // To support different native json object wrappings depending on native version.
        val native = JSONObject(nativeOrtbString).let { it.optJSONObject("native") ?: it }

        with(native) {
            Result.Success(
                NativeOrtbResponse(
                    version = if (has("ver")) getString("ver") else null,
                    assets = optJSONArray("assets").toAssets(),
                    link = optJSONObject("link").toLink(),
                    impressionTrackerUrls = optJSONArray("imptrackers").toStringList(),
                    eventTrackers = optJSONArray("eventtrackers").toEventTrackers(),
                    privacyUrl = if (has("privacy")) getString("privacy") else null
                )
            )
        }
    } catch (e: Exception) {
        Result.Failure(e.toString())
    }
}

private fun JSONArray?.toAssets(): List<NativeOrtbResponse.Asset> {
    if (this == null) return emptyList()

    val assets = mutableListOf<NativeOrtbResponse.Asset>()

    val len = length()
    for (i in 0 until len) {
        with(getJSONObject(i)) {
            // TODO. Change this logic when/if "assetsurl" field support is implemented
            //  (assets there might not contain "id" field).
            if (has("id")) {
                val id = getInt("id")
                val required = optInt("required", 0) == 1
                val link = optJSONObject("link").toLink()

                val asset =
                    optJSONObject("title").toTitleAsset(id, required, link)
                        ?: optJSONObject("img").toImageAsset(id, required, link)
                        ?: optJSONObject("video").toVideoAsset(id, required, link)
                        ?: optJSONObject("data").toDataAsset(id, required, link)

                if (asset != null) assets += asset
            }
        }
    }

    return assets
}

private fun JSONObject?.toTitleAsset(
    id: Int,
    required: Boolean,
    link: NativeOrtbResponse.Link?
): NativeOrtbResponse.Asset.Title? {
    if (this == null) return null

    return NativeOrtbResponse.Asset.Title(
        id,
        required,
        link,
        text = getString("text"),
        length = if (has("len")) getInt("len") else null
    )
}

private fun JSONObject?.toImageAsset(
    id: Int,
    required: Boolean,
    link: NativeOrtbResponse.Link?
): NativeOrtbResponse.Asset.Image? {
    if (this == null) return null

    return NativeOrtbResponse.Asset.Image(
        id,
        required,
        link,
        type = if (has("type")) getInt("type") else null,
        url = getString("url"),
        w = if (has("w")) getInt("w") else null,
        h = if (has("h")) getInt("h") else null
    )
}

private fun JSONObject?.toVideoAsset(
    id: Int,
    required: Boolean,
    link: NativeOrtbResponse.Link?
): NativeOrtbResponse.Asset.Video? {
    if (this == null) return null

    return NativeOrtbResponse.Asset.Video(
        id,
        required,
        link,
        vastTag = getString("vasttag")
    )
}

private fun JSONObject?.toDataAsset(
    id: Int,
    required: Boolean,
    link: NativeOrtbResponse.Link?
): NativeOrtbResponse.Asset.Data? {
    if (this == null) return null

    return NativeOrtbResponse.Asset.Data(
        id,
        required,
        link,
        type = if (has("type")) getInt("type") else null,
        len = if (has("len")) getInt("len") else null,
        value = getString("value")
    )
}

private fun JSONObject?.toLink(): NativeOrtbResponse.Link? {
    if (this == null) return null

    val url = getString("url")
    val fallbackUrl = if (has("fallback")) getString("fallback") else null
    val clickTrackerUrls = optJSONArray("clicktrackers").toStringList()

    return NativeOrtbResponse.Link(url, clickTrackerUrls, fallbackUrl)
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()

    val strings = mutableListOf<String>()

    val len = length()
    for (i in 0 until len) {
        strings += getString(i)
    }

    return strings
}

private fun JSONArray?.toEventTrackers(): List<NativeOrtbResponse.EventTracker> {
    if (this == null) return emptyList()

    val eventTrackers = mutableListOf<NativeOrtbResponse.EventTracker>()

    val len = length()
    for (i in 0 until len) {
        with(getJSONObject(i)) {
            eventTrackers += NativeOrtbResponse.EventTracker(
                eventType = getInt("event"),
                methodType = getInt("method"),
                url = if (has("url")) getString("url") else null,
                // TODO.
                customData = emptyMap()
            )
        }
    }

    return eventTrackers
}
