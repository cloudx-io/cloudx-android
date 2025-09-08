package io.cloudx.sdk.internal.ads.native

private val EventTrackers = listOf(
    NativeAdSpecs.EventTracker(
        NativeAdSpecs.EventTracker.Event.Impression,
        setOf(NativeAdSpecs.EventTracker.Method.Image)
    )
)

internal val NativeSmall = NativeAdSpecs(
    eventTrackers = EventTrackers,
    assets = listOf(
        Title(),
        SponsorText(),
        CTAText(),
        IconImage(),
        Description(),
        Rating()
    ).associateBy { it.id }
)

internal val NativeMediumImage = NativeAdSpecs(
    eventTrackers = EventTrackers,
    assets = listOf(
        Title(),
        SponsorText(),
        CTAText(),
        IconImage(),
        MainImage(),
        Description(),
        Rating()
    ).associateBy { it.id }
)

internal object NativeAdAssetIds {

    const val MAIN_IMAGE = 1
    const val ICON = 2
    const val CTA_TEXT = 3
    const val DESCRIPTION = 4
    const val RATING = 5
    const val SPONSOR_TEXT = 6
    const val TITLE = 7
}

private fun IconImage(required: Boolean = true) = NativeAdSpecs.Asset.Image(
    NativeAdAssetIds.ICON,
    required,
    NativeAdSpecs.Asset.Image.Type.Icon
)

private fun MainImage(required: Boolean = true) = NativeAdSpecs.Asset.Image(
    NativeAdAssetIds.MAIN_IMAGE,
    required,
    NativeAdSpecs.Asset.Image.Type.Main
)

private fun Title(required: Boolean = true) = NativeAdSpecs.Asset.Title(
    NativeAdAssetIds.TITLE,
    required,
    length = 70
)

private fun SponsorText(required: Boolean = false) = NativeAdSpecs.Asset.Data(
    NativeAdAssetIds.SPONSOR_TEXT,
    required,
    NativeAdSpecs.Asset.Data.Type.Sponsored,
    len = 25
)

private fun Description(required: Boolean = false) =
    NativeAdSpecs.Asset.Data(
        NativeAdAssetIds.DESCRIPTION,
        required,
        NativeAdSpecs.Asset.Data.Type.Description,
        len = 150
    )

private fun Rating(required: Boolean = false) = NativeAdSpecs.Asset.Data(
    NativeAdAssetIds.RATING,
    required,
    NativeAdSpecs.Asset.Data.Type.Rating,
    len = 5
)

private fun CTAText(required: Boolean = true) = NativeAdSpecs.Asset.Data(
    NativeAdAssetIds.CTA_TEXT,
    required,
    NativeAdSpecs.Asset.Data.Type.CTAText,
    len = 100
)