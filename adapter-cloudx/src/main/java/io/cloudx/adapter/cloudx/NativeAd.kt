package io.cloudx.adapter.cloudx

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import io.cloudx.cd.nativead.NativeOrtbResponse
import io.cloudx.cd.nativead.PreparedNativeAsset
import io.cloudx.cd.nativead.PreparedNativeAssets
import io.cloudx.cd.nativead.parseNativeOrtbResponse
import io.cloudx.cd.nativead.prepareNativeAssets
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapter
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterListener
import io.cloudx.sdk.internal.adapter.CloudXAdapterError
import io.cloudx.sdk.internal.nativead.NativeAdSpecs
import io.cloudx.sdk.internal.nativead.viewtemplates.CloudXNativeAdViewTemplate
import io.cloudx.sdk.internal.nativead.viewtemplates.cloudXNativeAdTemplate
import io.cloudx.cd.staticrenderer.ExternalLinkHandlerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class NativeAd(
    private val activity: Activity,
    private val adViewContainer: CloudXAdViewAdapterContainer,
    private val adm: String,
    private val adType: AdType.Native,
    private val listener: CloudXAdViewAdapterListener
) : CloudXAdViewAdapter {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var cloudXNativeAdViewTemplate: CloudXNativeAdViewTemplate? = null
    private val externalLinkHandler = ExternalLinkHandlerImpl(activity)

    override fun load() {
        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val nativeAdTemplate = inflater.cloudXNativeAdTemplate(adType)
        cloudXNativeAdViewTemplate = nativeAdTemplate

        scope.launch {
            when (val parsingResult = parseNativeOrtbResponse(adm)) {
                is Result.Failure -> {
                    listener.onError(CloudXAdapterError(description = parsingResult.value))
                }

                is Result.Success -> when (val assetsResult =
                    prepareNativeAssets(parsingResult.value.assets)) {
                    is Result.Failure -> {
                        listener.onError(CloudXAdapterError(description = assetsResult.value))
                    }

                    is Result.Success -> if (
                        nativeAdTemplate.bindAssetsAndEvents(
                            parsingResult.value,
                            assetsResult.value
                        )
                    ) {
                        listener.onLoad()

                        adViewContainer.onAdd(nativeAdTemplate.rootView)

                        listener.onShow()
                        listener.onImpression()
                    } else {
                        listener.onError(CloudXAdapterError(description = "can't bind required assets: some were missing or had wrong ids"))
                    }
                }
            }
        }
    }

    private suspend fun CloudXNativeAdViewTemplate.bindAssetsAndEvents(
        nativeOrtbResponse: NativeOrtbResponse,
        preparedNativeAssets: PreparedNativeAssets
    ): Boolean {
        val adSpecs = adType.specs

        // Asset-view binding.
        for ((assetId, spec) in adSpecs.assets) {
            val preparedAsset = preparedNativeAssets.allNonFailedAssets[assetId]
            // Required asset failed to load/missing.
            if (
                spec.required && preparedAsset == null
                || preparedAsset?.isAssetTypeMatches(spec) == false
            ) {
                return false
            }

            when (spec) {
                is NativeAdSpecs.Asset.Data -> {
                    val text = (preparedAsset as? PreparedNativeAsset.Data)?.value
                    when (spec.type) {
                        NativeAdSpecs.Asset.Data.Type.Description -> {
                            descriptionText = text
                        }

                        NativeAdSpecs.Asset.Data.Type.CTAText -> {
                            callToActionText = text
                        }

                        else -> { /* no-op */
                        }
                    }
                }

                is NativeAdSpecs.Asset.Image -> {
                    val img =
                        loadImageFromWebUrl((preparedAsset as? PreparedNativeAsset.Image)?.precachedAssetUri)
                    img?.let {
                        when (spec.type) {
                            NativeAdSpecs.Asset.Image.Type.Icon -> {
                                appIcon = img
                            }

                            NativeAdSpecs.Asset.Image.Type.Main -> {
                                mainImage = img
                            }
                        }
                    }
                }

                is NativeAdSpecs.Asset.Title -> title = (preparedAsset as? PreparedNativeAsset.Title)?.text
                is NativeAdSpecs.Asset.Video -> { /* no-op */
                }
            }
        }

        // Click event.
        nativeOrtbResponse.link?.let { clickLink ->
            onClick = {
                externalLinkHandler(clickLink.url)

                scope.launch {
                    clickLink.clickTrackerUrls.onEach {
                        it.sendGet()
                    }
                }

                listener.onClick()
            }
        }

        scope.launch {
            // Impression events
            nativeOrtbResponse.impressionTrackerUrls.onEach {
                it.sendGet()
            }

            nativeOrtbResponse.eventTrackers.filter {
                it.eventType == NativeAdSpecs.EventTracker.Event.Impression.value
            }.onEach {
                it.url?.sendGet()
            }
        }

        return true
    }

    override fun destroy() {
        scope.cancel()
        cloudXNativeAdViewTemplate?.rootView?.let {
            adViewContainer.onRemove(it)
        }
        cloudXNativeAdViewTemplate = null
    }
}

private fun PreparedNativeAsset.isAssetTypeMatches(assetSpec: NativeAdSpecs.Asset): Boolean =
    when (assetSpec) {
        is NativeAdSpecs.Asset.Data -> this is PreparedNativeAsset.Data
        is NativeAdSpecs.Asset.Image -> this is PreparedNativeAsset.Image
        is NativeAdSpecs.Asset.Title -> this is PreparedNativeAsset.Title
        is NativeAdSpecs.Asset.Video -> false
    }

private suspend fun loadImageFromWebUrl(url: String?): Drawable? =
    withContext(Dispatchers.IO) {
        try {
            (URL(url).content as InputStream).use {
                Drawable.createFromStream(it, null)
            }
        } catch (e: Exception) {
            null
        }
    }


private suspend fun String.sendGet() = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = URL(this@sendGet).openConnection() as HttpURLConnection
        with(connection) {
            requestMethod = "GET"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                inputStream.bufferedReader().use {
                    try {
                        it.lines().forEach { line ->
                            println(line)
                        }
                    } catch (e: Exception) {
                        //
                    }
                }
            } else {
                val reader: BufferedReader = inputStream.bufferedReader()
                reader.use {
                    try {
                        var line: String? = reader.readLine()
                        while (line != null) {
                            line = reader.readLine()
                        }
                    } catch (e: Exception) {
                        //
                    }
                }
            }
        }
    } catch (e: Exception) {
        connection?.disconnect()
    }
}