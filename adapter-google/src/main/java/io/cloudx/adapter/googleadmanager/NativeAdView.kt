package io.cloudx.adapter.googleadmanager

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.ads.native.viewtemplates.cloudXNativeAdTemplate

internal fun Activity.createNativeAdView(adType: AdType.Native, ad: NativeAd): NativeAdView {
    val nativeAdView = NativeAdView(this)

    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val cloudxNativeAdTemplate = inflater.cloudXNativeAdTemplate(adType)

    with(cloudxNativeAdTemplate) {
        title = ad.headline
        descriptionText = ad.body
        appIcon = ad.icon?.drawable
        callToActionText = ad.callToAction
        mainImage = ad.images.firstOrNull()?.drawable
        customMediaView = MediaView(this@createNativeAdView)
    }

    with (nativeAdView) {
        addView(
            cloudxNativeAdTemplate.rootView,
            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )

        headlineView = cloudxNativeAdTemplate.titleView
        callToActionView = cloudxNativeAdTemplate.ctaView
        bodyView = cloudxNativeAdTemplate.descriptionView
        iconView = cloudxNativeAdTemplate.iconView
        mediaView = cloudxNativeAdTemplate.customMediaView as? MediaView

        setNativeAd(ad)
    }

    return nativeAdView
}

