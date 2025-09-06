package io.cloudx.sdk.internal.ads.native.viewtemplates

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import io.cloudx.sdk.R
import io.cloudx.sdk.internal.AdType

fun LayoutInflater.cloudXNativeAdTemplate(adType: AdType.Native): CloudXNativeAdViewTemplate =
    when (adType) {
        AdType.Native.Small -> cloudXNativeAdSmallViewTemplate()
        AdType.Native.Medium -> cloudXNativeAdMediumViewTemplate()
    }

private fun LayoutInflater.cloudXNativeAdSmallViewTemplate(): CloudXNativeAdViewTemplate {

    val layout = inflate(R.layout.cloudx_native_small_template, null, false) as ViewGroup

    return NativeAdViewTemplate(
        rootView = layout,
        ctaView = layout.findViewById(R.id.cta),
        titleView = layout.findViewById(R.id.title),
        descriptionView = layout.findViewById(R.id.description),
        iconView = layout.findViewById(R.id.icon),
        mainImageView = layout.findViewById(R.id.main_image),
        customMediaViewContainer = null
    )
}

private fun LayoutInflater.cloudXNativeAdMediumViewTemplate(): CloudXNativeAdViewTemplate {

    val layout = inflate(R.layout.cloudx_native_medium_template, null, false) as ViewGroup

    return NativeAdViewTemplate(
        rootView = layout,
        ctaView = layout.findViewById(R.id.cta),
        titleView = layout.findViewById(R.id.title),
        descriptionView = layout.findViewById(R.id.description),
        iconView = layout.findViewById(R.id.icon),
        mainImageView = layout.findViewById(R.id.main_image),
        customMediaViewContainer = layout.findViewById(R.id.main_content_container)
    )
}

private class NativeAdViewTemplate(
    override val rootView: ViewGroup,
    override val ctaView: AppCompatButton?,
    override val titleView: AppCompatTextView?,
    override val descriptionView: AppCompatTextView?,
    override val iconView: AppCompatImageView?,
    override val mainImageView: AppCompatImageView?,
    private val customMediaViewContainer: FrameLayout?
) : CloudXNativeAdViewTemplate {

    override var title: String?
        get() = titleView?.text?.toString()
        set(value) {
            titleView?.text = value
        }

    override var descriptionText: String?
        get() = descriptionView?.text?.toString()
        set(value) {
            descriptionView?.text = value
        }

    override var callToActionText: String?
        get() = ctaView?.text?.toString()
        set(value) {
            ctaView?.text = value
        }

    override var appIcon: Drawable?
        get() = iconView?.drawable
        set(value) {
            iconView?.setImageDrawable(value)
        }

    override var mainImage: Drawable?
        get() = mainImageView?.drawable
        set(value) {
            mainImageView?.setImageDrawable(value)
        }

    override var customMediaView: View? = null
        set(newCustomMediaView) {
            if (customMediaViewContainer == null) return

            val oldCustomMediaView = field
            field = newCustomMediaView

            customMediaViewContainer.removeView(oldCustomMediaView)
            customMediaViewContainer.addView(
                newCustomMediaView,
                LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
        }

    override var onClick: (() -> Unit)? = null
        set(value) {
            if (ctaView == null) return

            field = value

            ctaView.setOnClickListener {
                value?.invoke()
            }
        }
}