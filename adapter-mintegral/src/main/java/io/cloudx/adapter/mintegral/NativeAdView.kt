package io.cloudx.adapter.mintegral

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.core.view.setMargins
import com.bumptech.glide.Glide
import com.mbridge.msdk.nativex.view.MBMediaView
import com.mbridge.msdk.out.Campaign
import com.mbridge.msdk.out.MBBidNativeHandler
import com.mbridge.msdk.out.OnMBMediaViewListener
import com.mbridge.msdk.widget.MBAdChoice
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.ads.native.viewtemplates.cloudXNativeAdTemplate

internal fun Activity.createNativeAdView(
    adHandler: MBBidNativeHandler,
    adType: AdType.Native,
    campaign: Campaign,
    onClick: () -> Unit
): ViewGroup {
    val activity = this
    val appCtx = activity.applicationContext
    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val cloudxNativeAdTemplate = inflater.cloudXNativeAdTemplate(adType)

    with(cloudxNativeAdTemplate) {
        // Run only when media view is supported via cloudx template.
        customMediaView = MBMediaView(activity)
        (customMediaView as? MBMediaView)?.let {
            it.visibility = VISIBLE
            it.setNativeAd(campaign)
            it.setOnMediaViewListener(createOnMediaViewListener(onClick))
        }

        iconView?.let {
            Glide.with(appCtx).load(campaign.iconUrl).into(it)
        }

        title = campaign.appName

        descriptionText = campaign.appDesc

        callToActionText = campaign.adCall
    }

    // Not ideal, but should do for now.
    // AdChoices view overlay support.
    val container = FrameLayout(activity)
    container.addView(cloudxNativeAdTemplate.rootView)

    // Adding Ad Choices overlay.
    val adChoiceView = MBAdChoice(activity)
    adChoiceView.setCampaign(campaign)

    container.addView(
        adChoiceView,
        LayoutParams(
            campaign.adchoiceSizeWidth.let { if (it == 0) WRAP_CONTENT else it },
            campaign.adchoiceSizeHeight.let { if (it == 0) WRAP_CONTENT else it },
            Gravity.TOP or Gravity.END,
        ).apply {
            setMargins(6)
        }
    )

    adHandler.registerView(container, campaign)

    return container
}

private fun createOnMediaViewListener(onClick: () -> Unit) = object : OnMBMediaViewListener {
    override fun onEnterFullscreen() {
    }

    override fun onExitFullscreen() {
    }

    override fun onStartRedirection(p0: Campaign?, p1: String?) {
    }

    override fun onFinishRedirection(p0: Campaign?, p1: String?) {
    }

    override fun onRedirectionFailed(p0: Campaign?, p1: String?) {
    }

    override fun onVideoAdClicked(p0: Campaign?) {
        onClick()
    }

    override fun onVideoStart() {
    }
}