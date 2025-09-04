package io.cloudx.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.setPadding
import io.cloudx.sdk.internal.AdViewSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import io.cloudx.sdk.internal.CloudXLogger
import io.cloudx.sdk.internal.ad_banner.Banner
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.common.createViewabilityTracker
import io.cloudx.sdk.internal.common.dpToPx
import io.cloudx.sdk.internal.core.ad.suspendable.SuspendableBanner
import io.cloudx.sdk.internal.PlacementLoopIndexTracker

interface CloudXAdViewListener : CloudXAdListener{
    /**
     * User manually expanded the ad banner.
     */
    fun onAdExpanded(placementName: String)

    /**
     * User manually closed the ad banner. It is the responsibility of the publisher to reload it again.
     */
    fun onAdCollapsed(placementName: String)
}

@SuppressLint("ViewConstructor")
class CloudXAdView internal constructor(
    activity: Activity,
    private var suspendPreloadWhenInvisible: Boolean,
    private var adViewSize: AdViewSize,
    internal val createBanner: (
        adViewContainer: CloudXAdViewAdapterContainer,
        bannerVisibility: StateFlow<Boolean>,
        suspendPreloadWhenInvisible: Boolean,
    ) -> Banner,
    private val placementName: String,
    private val hasCloseButton: Boolean
) : FrameLayout(activity), Destroyable {

    private val TAG = "CloudXAdView"
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private var banner: Banner? = null

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        isBannerShown.value = visibility == VISIBLE
    }

    private val isBannerShown = MutableStateFlow(isShown)

    private val viewabilityTracker = createViewabilityTracker(mainScope, isBannerShown)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (banner != null) return

        banner = createBanner(
            createBannerContainer(),
            viewabilityTracker.isViewable,
            suspendPreloadWhenInvisible,
        )

        updateBannerListener()
    }

    fun show() {
        mainScope.launch {
            visibility = View.VISIBLE
        }
    }

    fun hide() {
        mainScope.launch {
            visibility = View.GONE
        }
    }

    override fun destroy() {
        mainScope.launch {
            banner?.destroy()
            banner = null

            (parent as? ViewGroup)?.removeView(this@CloudXAdView)

            mainScope.cancel()

            viewabilityTracker.destroy()

            PlacementLoopIndexTracker.reset(placementName)
        }
    }

    var listener: CloudXAdViewListener? = null
        set(value) {
            field = value
            updateBannerListener()
        }

    private fun updateBannerListener() {
        banner?.listener = listener
    }

    // So the idea is to create a banner container per each onAdd() call
    // and put the created invisible container with the banner view inside and put it to the "back" of the view.
    // So that we can have some sort of a banner collection / precaching kind of thing
    // without sharing a single viewgroup with only foreground banner visible.
    private fun createBannerContainer() = object : CloudXAdViewAdapterContainer {
        override fun onAdd(bannerView: View) {
            insertBannerContainerToTheBackground(bannerView)
        }

        override fun onRemove(bannerView: View) {
            removeBanner(bannerView)
        }

        override fun acquireBannerContainer(): ViewGroup {
            return insertBannerContainerToTheBackground(null)
        }

        override fun releaseBannerContainer(bannerContainer: ViewGroup) {
            removeBanner(bannerContainer)
        }
    }

    // Ordered by layer: background first, foreground - last.
    // TODO. View is null for acquireBannerContainer() call... Fyber... ugh.
    private val orderedBannerToContainerList = mutableListOf<Pair<View?, ViewGroup>>()

    private fun updateForegroundBannerVisibility() {
        orderedBannerToContainerList.lastOrNull()?.second?.visibility = VISIBLE
    }

    private fun layoutMatchParent() = LayoutParams(MATCH_PARENT, MATCH_PARENT)

    private fun insertBannerContainerToTheBackground(bannerViewToAdd: View?): ViewGroup {
        val bannerContainer = FrameLayout(context)
        bannerContainer.visibility = GONE

        if (bannerViewToAdd != null) {
            // Tentative fix for: InMobi IllegalStateException.
            // I suppose that sometimes InMobi returns the same banner instance,
            // therefore it's probably already added to the CloudXAdView, the second take causes IllegalStateException.
            // I haven't been able to reproduce the bug yet, so I decided to wrap with try catch and log stuff
            try {
                bannerContainer.addView(
                    bannerViewToAdd,
                    LayoutParams(
                        context.dpToPx(adViewSize.w),
                        context.dpToPx(adViewSize.h),
                        Gravity.CENTER
                    )
                )

                if (hasCloseButton) {
                    val closeButton = ImageButton(context).apply {
                        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                        background = null
                        scaleType = ImageView.ScaleType.CENTER
                        setOnClickListener {
                            // TODO this will never succeed consider removing close button altogether
//                            banner?.let {
//                                val adNetwork = (it as? SuspendableBanner)?.bidderName
//                                listener?.onAdHidden(CloudXAd(adNetwork))
//                            }

                            listener?.onAdCollapsed(placementName)
                            PlacementLoopIndexTracker.reset(placementName)
                            destroy()
                        }
                        setPadding(context.dpToPx(2))
                    }

                    val closeBtnSize = context.dpToPx(12)
                    val closeBtnParams = LayoutParams(closeBtnSize, closeBtnSize).apply {
                        gravity = Gravity.END or Gravity.TOP
                        topMargin = context.dpToPx(4)
                        marginEnd = context.dpToPx(4)
                    }

                    bannerContainer.addView(closeButton, closeBtnParams)
                }

                CloudXLogger.info(TAG, msg = "added banner view to the background layer: ${bannerViewToAdd.javaClass.simpleName}")

            } catch (e: Exception) {
                CloudXLogger.error(
                    TAG,
                    msg = "CloudXAdView exception during adding ad view ${bannerViewToAdd.javaClass.simpleName}: $e",
                    forceLogging = true
                )
            }
        }

        addView(bannerContainer, 0, layoutMatchParent())

        orderedBannerToContainerList.add(
            0,
            // Let's just insert empty placeholder for the faulty banner as a quick workaround.
            bannerViewToAdd?.takeIf { it.parent == bannerContainer } to bannerContainer
        )

        updateForegroundBannerVisibility()

        return bannerContainer
    }

    private fun removeBanner(bannerView: View) =
        removeBanner(orderedBannerToContainerList.indexOfFirst { it.first == bannerView })

    private fun removeBanner(bannerContainer: ViewGroup) =
        removeBanner(orderedBannerToContainerList.indexOfFirst { it.second == bannerContainer })

    private fun removeBanner(idx: Int) {
        if (idx >= 0) removeView(orderedBannerToContainerList.removeAt(idx).second)

        updateForegroundBannerVisibility()
    }
}