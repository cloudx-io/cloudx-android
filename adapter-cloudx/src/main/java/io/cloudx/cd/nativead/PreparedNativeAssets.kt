package io.cloudx.cd.nativead

internal class PreparedNativeAssets(
    val data: Map<Int, PreparedNativeAsset.Data>,
    val images: Map<Int, PreparedNativeAsset.Image>,
    val titles: Map<Int, PreparedNativeAsset.Title>,
    val failedAssets: List<Pair<NativeOrtbResponse.Asset, String>>
) {
    val allNonFailedAssets by lazy {
        data + images + titles
    }
}

// Prepared asset via precaching or other means in order to be prepared for rendering.
internal sealed class PreparedNativeAsset(val originAsset: NativeOrtbResponse.Asset) {

    val id: Int = originAsset.id
    val required: Boolean = originAsset.required
    val link: NativeOrtbResponse.Link? = originAsset.link

    class Title(
        originAsset: NativeOrtbResponse.Asset.Title
    ) : PreparedNativeAsset(originAsset) {

        val text: String = originAsset.text
    }

    class Image(
        originAsset: NativeOrtbResponse.Asset.Image,
        val precachedAssetUri: String
    ) : PreparedNativeAsset(originAsset)

    class Data(
        originAsset: NativeOrtbResponse.Asset.Data
    ) : PreparedNativeAsset(originAsset) {

        val value = originAsset.value
    }
}
