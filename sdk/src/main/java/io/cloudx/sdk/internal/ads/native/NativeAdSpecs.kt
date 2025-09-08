package io.cloudx.sdk.internal.ads.native

/**
 * Respective classes and field names required for a proper ortb bid request generation according to
 * [_OpenRTB Dynamic Native Ads API Specification_](https://www.iab.com/wp-content/uploads/2018/03/OpenRTB-Native-Ads-Specification-Final-1.2.pdf)
 * And also ad rendering, more specifically, asset and view stitching.
 * @param assets - map of assets required for a proper ad rendering; key - [Asset.id]
 */
class NativeAdSpecs(
    val assets: Map<Int, Asset>,
    val eventTrackers: List<EventTracker>
) {

    sealed class Asset(
        val id: Int,
        val required: Boolean,
    ) {

        class Title(id: Int, required: Boolean, val length: Int) : Asset(id, required)

        class Image(
            id: Int,
            required: Boolean,
            val type: Type
        ) : Asset(id, required) {

            enum class Type(val value: Int) {
                Icon(1),
                Main(3)
            }
        }

        class Video(
            id: Int,
            required: Boolean
        ) : Asset(id, required)

        class Data(
            id: Int,
            required: Boolean,
            val type: Type,
            val len: Int?
        ) : Asset(id, required) {

            enum class Type(val value: Int) {
                Sponsored(1),
                Description(2),
                Rating(3),
                Likes(4),
                Downloads(5),
                Price(6),
                SalePrice(7),
                Phone(8),
                Address(9),
                Description2(10),
                DisplayUrl(11),
                CTAText(12),
            }
        }
    }

    class EventTracker(val event: Event, val methodTypes: Set<Method>) {

        enum class Event(val value: Int) {
            Impression(1)
        }

        enum class Method(val value: Int) {
            Image(1)
        }
    }
}