package io.cloudx.sdk

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.CXSdk
import io.cloudx.sdk.internal.adapter.CloudXAdViewAdapterContainer
import io.cloudx.sdk.internal.ads.AdFactory
import io.cloudx.sdk.internal.ads.banner.BannerManager
import io.cloudx.sdk.internal.common.createViewabilityTracker
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.internal.size
import io.cloudx.sdk.internal.tracker.PlacementLoopIndexTracker
import io.cloudx.sdk.internal.util.ThreadUtils
import io.cloudx.sdk.internal.util.dpToPx
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class CloudXAdView internal constructor(
    private val placementName: String,
    private val adType: AdType,
) : FrameLayout(ApplicationContext()), CloudXDestroyable {

    private val logger = CXLogger.forPlacement("CloudXAdView", placementName)

    private val mainScope = ThreadUtils.createMainScope("CloudXAdView")
    private val initJob: Job

    // State management
    private val isBannerShown = MutableStateFlow(isShown)
    private val viewabilityTracker = createViewabilityTracker(mainScope, isBannerShown)

    // Banner management
    private var bannerManager: BannerManager? = null

    // Banner container tracking - ordered by layer: background first, foreground last
    // TODO. View is null for acquireBannerContainer() call... Fyber
    private val orderedBannerToContainerList = mutableListOf<Pair<View?, ViewGroup>>()
    private var hasCloseButton = false

    var listener: CloudXAdViewListener? = null
        set(value) {
            field = value
            updateBannerListener()
        }

    init {
        initJob = mainScope.launch {
            val initState = CXSdk.initState.first { it is InitializationState.Initialized }
                    as InitializationState.Initialized
            val adFactory = initState.initializationService.adFactory
            bannerManager = adFactory!!.createBannerManager(
                AdFactory.CreateBannerParams(
                    adType = adType,
                    adViewAdapterContainer = createBannerContainer(),
                    bannerVisibility = viewabilityTracker.isViewable,
                    placementName = placementName,
                )
            )
            updateBannerListener()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        isBannerShown.value = visibility == VISIBLE
    }

    // Public API
    fun load() {
        if (bannerManager != null) {
            bannerManager?.load()
            return
        }

        if (CXSdk.initState.value is InitializationState.Uninitialized) {
            val error = CloudXErrorCode.NOT_INITIALIZED.toCloudXError()
            logger.e(error.effectiveMessage)
            listener?.onAdLoadFailed(error)
        }
    }

    /**
     * Start auto-refresh for banner ads.
     * Banners will automatically refresh at the configured interval.
     */
    fun startAutoRefresh() {
        mainScope.launch {
            // Wait for initialization to complete if needed
            initJob.join()
            bannerManager?.startAutoRefresh()
        }
    }

    /**
     * Stop auto-refresh for banner ads.
     * The current banner will remain displayed but no new banners will be loaded.
     */
    fun stopAutoRefresh() {
        mainScope.launch {
            // Wait for initialization to complete if needed
            initJob.join()
            bannerManager?.stopAutoRefresh()
        }
    }

    override fun destroy() {
        mainScope.launch {
            bannerManager?.destroy()
            bannerManager = null

            (parent as? ViewGroup)?.removeView(this@CloudXAdView)

            mainScope.cancel()
            initJob.cancel()

            viewabilityTracker.destroy()

            PlacementLoopIndexTracker.reset(placementName)
        }
    }

    // Private methods - Banner management
    private fun updateBannerListener() {
        bannerManager?.listener = listener
    }

    // Banner container creation
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

    // Banner container management
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
                val adSize = adType.size()
                bannerContainer.addView(
                    bannerViewToAdd,
                    LayoutParams(
                        context.dpToPx(adSize.w),
                        context.dpToPx(adSize.h),
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

//                            listener?.onAdCollapsed(placementName)
                            PlacementLoopIndexTracker.reset(placementName)
                            destroy()
                        }
                        val padding = context.dpToPx(2)
                        setPadding(padding, padding, padding, padding)
                    }

                    val closeBtnSize = context.dpToPx(12)
                    val closeBtnParams = LayoutParams(closeBtnSize, closeBtnSize).apply {
                        gravity = Gravity.END or Gravity.TOP
                        topMargin = context.dpToPx(4)
                        marginEnd = context.dpToPx(4)
                    }

                    bannerContainer.addView(closeButton, closeBtnParams)
                }

                logger.i("added banner view to the background layer: ${bannerViewToAdd.javaClass.simpleName}")

            } catch (e: Exception) {
                logger.e("CloudXAdView exception during adding ad view ${bannerViewToAdd.javaClass.simpleName}", e)
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

    // Banner removal methods
    private fun removeBanner(bannerView: View) =
        removeBanner(orderedBannerToContainerList.indexOfFirst { it.first == bannerView })

    private fun removeBanner(bannerContainer: ViewGroup) =
        removeBanner(orderedBannerToContainerList.indexOfFirst { it.second == bannerContainer })

    private fun removeBanner(idx: Int) {
        if (idx >= 0) removeView(orderedBannerToContainerList.removeAt(idx).second)
        updateForegroundBannerVisibility()
    }
}
