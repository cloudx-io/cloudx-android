package io.cloudx.sdk.internal.ads.native.viewtemplates

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

interface CloudXNativeAdViewTemplate {

    var title: String?
    var descriptionText: String?
    var callToActionText: String?
    var appIcon: Drawable?
    var mainImage: Drawable?

    var onClick: (() -> Unit)?

    /**
     * Root view -- reference to the template's container view, which contains all the views defined in this interface.
     */
    val rootView: ViewGroup

    val ctaView: View?
    val titleView: View?
    val descriptionView: View?
    val iconView: ImageView?
    val mainImageView: ImageView?
    var customMediaView: View?
}