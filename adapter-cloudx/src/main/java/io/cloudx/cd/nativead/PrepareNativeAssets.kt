package io.cloudx.cd.nativead

import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// TODO. Asset preparation Failure default/overridable logic (if required and not loaded -- fail ... or custom logic)
internal suspend fun prepareNativeAssets(
    assets: List<NativeOrtbResponse.Asset>
): Result<PreparedNativeAssets, String> {

    val groupedAssets = assets.groupBy { it.required }
    val requiredAssetsGroup = groupedAssets[true] ?: listOf()
    val optionalAssetsGroup = groupedAssets[false] ?: listOf()

    // Loading required assets firsts, fail everything immediately if any required asset doesn't get "prepared".
    val preparedRequiredAssets = try {
        coroutineScope {
            requiredAssetsGroup.map { asset ->
                async {
                    when (val result = prepareNativeAsset(asset)) {
                        is Result.Failure -> throw Exception(result.value)
                        is Result.Success -> asset to result
                    }
                }
            }.awaitAll()
        }
    } catch (e: Exception) {
        return Result.Failure("required asset failed to prepare: $e")
    }

    val preparedOptionalAssets = coroutineScope {
        optionalAssetsGroup.map { asset ->
            async { asset to prepareNativeAsset(asset) }
        }.awaitAll()
    }

    val data = mutableMapOf<Int, PreparedNativeAsset.Data>()
    val images = mutableMapOf<Int, PreparedNativeAsset.Image>()
    val titles = mutableMapOf<Int, PreparedNativeAsset.Title>()
    val failedAssets = mutableListOf<Pair<NativeOrtbResponse.Asset, String>>()

    for ((originAsset, preparedNativeAssetResult) in preparedRequiredAssets + preparedOptionalAssets) {
        when (preparedNativeAssetResult) {
            is Result.Failure -> failedAssets += originAsset to preparedNativeAssetResult.value
            is Result.Success -> when (val res = preparedNativeAssetResult.value) {
                is PreparedNativeAsset.Data -> data += res.originAsset.id to res
                is PreparedNativeAsset.Image -> images += res.originAsset.id to res
                is PreparedNativeAsset.Title -> titles += res.originAsset.id to res
            }
        }
    }

    return Result.Success(PreparedNativeAssets(data, images, titles, failedAssets))
}

private suspend fun prepareNativeAsset(
    asset: NativeOrtbResponse.Asset,
): Result<PreparedNativeAsset, String> = when (asset) {

    is NativeOrtbResponse.Asset.Data -> Result.Success(
        PreparedNativeAsset.Data(asset)
    )

    is NativeOrtbResponse.Asset.Image -> Result.Success(
        PreparedNativeAsset.Image(
            asset,
            asset.url
        )
    )

    is NativeOrtbResponse.Asset.Title -> Result.Success(
        PreparedNativeAsset.Title(asset)
    )

    is NativeOrtbResponse.Asset.Video -> Result.Failure("video not supported")
}