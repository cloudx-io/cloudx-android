package io.cloudx.cd.nativead

internal class NativeOrtbResponse(
    val version: String?,
    val assets: List<Asset>,
    val link: Link?,
    val impressionTrackerUrls: List<String>,
    val eventTrackers: List<EventTracker>,
    val privacyUrl: String?
) {

    sealed class Asset(
        val id: Int,
        val required: Boolean,
        val link: Link?
    ) {
        class Title(
            id: Int,
            required: Boolean,
            link: Link?,
            val text: String,
            val length: Int?
        ) : Asset(id, required, link)

        class Image(
            id: Int,
            required: Boolean,
            link: Link?,
            val type: Int?,
            val url: String,
            val w: Int?,
            val h: Int?,
        ) : Asset(id, required, link)

        class Video(
            id: Int,
            required: Boolean,
            link: Link?,
            val vastTag: String
        ) : Asset(id, required, link)

        class Data(
            id: Int,
            required: Boolean,
            link: Link?,
            val type: Int?,
            val len: Int?,
            val value: String
        ) : Asset(id, required, link)
    }

    /**
     * call-to-action/clickthrough/destination ad link: whatever you want to call it.
     */
    class Link(
        val url: String,
        val clickTrackerUrls: List<String>,
        val fallbackUrl: String?
    )

    class EventTracker(
        val eventType: Int,
        val methodType: Int,
        val url: String?,
        val customData: Map<String, String>
    )
}
