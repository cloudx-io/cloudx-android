package io.cloudx.demo.demoapp

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import io.cloudx.demo.demoapp.loglistview.commonLogTagListRules
import io.cloudx.demo.demoapp.loglistview.setupLogListView
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXAdView
import io.cloudx.sdk.internal.AdType
import io.cloudx.sdk.internal.AdViewSize
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

abstract class BannerProgrammaticFragment: Fragment(R.layout.fragment_banner_programmatic) {

    private val bannerAdViews = MutableStateFlow<List<CloudXAdView>>(emptyList())

    private lateinit var llAds: LinearLayout
    private lateinit var loadShowButton: Button
    private lateinit var stopButton: Button

    private lateinit var placements: ArrayList<String?>
    private lateinit var logTag: String

    abstract fun logTagFilterRule(logTag: String, forTag: String): String?

    abstract fun createAdView(
        activity: Activity, placementName: String, listener: CloudXAdViewListener?
    ): CloudXAdView?

    abstract val adViewSize: AdViewSize

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()

        placements = args.getPlacements()
        logTag = args.getString(KEY_LOG_TAG)!!

        with(view) {
            loadShowButton = findViewById(R.id.btn_load_show)
            loadShowButton.setOnClickListener { onLoadShowClick() }

            stopButton = findViewById(R.id.btn_stop)
            stopButton.setOnClickListener { onStopClick() }

            llAds = view.findViewById(R.id.llAds)
        }

        placements.forEachIndexed { index, placementName ->
            if (placementName.isNullOrBlank()) return@forEachIndexed

            val adContainer = AdContainerLayout(requireContext())
            adContainer.setPlacement(placementName, adViewSize)
            llAds.addView(
                adContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Button visibility update depending on CloudX banner object availability.
        viewLifecycleOwner.repeatOnStart {
            bannerAdViews.collectLatest {
                val bannerAdViewExists = it.isNotEmpty()
                loadShowButton.visibility = bannerAdViewExists.not().toVisibility()
                stopButton.visibility = bannerAdViewExists.toVisibility()
            }
        }

        setupLogListView(view.findViewById(R.id.log_list)) { forTag ->
            logTagFilterRule(logTag, forTag)
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAdViews.value.forEach { it.show() }
    }

    override fun onPause() {
        super.onPause()
        bannerAdViews.value.forEach { it.hide() }
    }

    private fun onLoadShowClick() {
        val newBannerAdViews = mutableListOf<CloudXAdView>()
        placements.forEachIndexed { index, placementName ->
            if (placementName.isNullOrBlank()) return@forEachIndexed
            val bannerAdView = createAdView(requireActivity(), placementName, createBannerListener(placementName))
            if (bannerAdView == null) {
                CXLogger.e(
                    logTag,
                    "Can't create banner ad: SDK is not initialized or $placementName placement is missing in SDK config"
                )
            } else {
                CXLogger.i(logTag, "Banner ad created for \"$placementName\" placement")
                bannerAdView.visibility = VISIBLE
                val adContainer = llAds.getChildAt(index) as AdContainerLayout
                adContainer.addAdView(bannerAdView)
                newBannerAdViews.add(bannerAdView)
            }
        }
        bannerAdViews.value = newBannerAdViews
    }

    private fun onStopClick() {
        destroyBanners()
    }

    private fun destroyBanners() {
        bannerAdViews.value.forEach { it.destroy() }
        bannerAdViews.value = emptyList()
        llAds.removeAllViews()
        placements.forEach { placementName ->
            if (placementName.isNullOrBlank()) return@forEach
            val adContainer = AdContainerLayout(requireContext())
            adContainer.setPlacement(placementName, adViewSize)
            llAds.addView(
                adContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        CXLogger.i(logTag, "Banner ads destroyed for placements: ${placements.joinToString()}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        destroyBanners()
    }

    private fun createBannerListener(placementName: String) =
        object: CloudXAdViewListener, CloudXAdListener by LoggedCloudXAdListener(
            logTag = logTag, placementName = placementName
        ) {
            override fun onAdExpanded(placementName: String) {
                CXLogger.i(logTag, "Ad expanded by user: $placementName")
            }

            override fun onAdCollapsed(placementName: String) {
                CXLogger.i(logTag, "Ad closed by user: $placementName")
                destroyBanners()
            }
        }

    companion object {

        fun createArgs(
            placements: ArrayList<String>,
            logTag: String,
        ): Bundle = Bundle().apply {
            putPlacements(placements)
            putString(KEY_LOG_TAG, logTag)
        }

        private const val KEY_LOG_TAG = "KEY_LOG_TAG"
    }
}

private fun Boolean.toVisibility(): Int = if (this) VISIBLE else GONE

fun ConstraintLayout.setBannerViewSize(bannerPlaceholderResId: Int, size: AdViewSize) {
    val cl = this
    val cs = ConstraintSet()

    cs.clone(cl)
    with(cs) {
        val koef = resources.displayMetrics?.density ?: 1f

        constrainWidth(bannerPlaceholderResId, (size.w * koef).toInt())
        constrainHeight(bannerPlaceholderResId, (size.h * koef).toInt())
    }
    cs.applyTo(cl)
}

class StandardBannerProgrammaticFragment: BannerProgrammaticFragment() {

    override fun logTagFilterRule(logTag: String, forTag: String): String? =
        commonLogTagListRules(forTag) ?: when (forTag) {
            "CX:$logTag", "CX:BannerManager" -> "Banner"
            else -> null
        }

    override fun createAdView(
        activity: Activity, placementName: String, listener: CloudXAdViewListener?
    ): CloudXAdView? =
        CloudX.createBanner(activity, placementName, listener)

    override val adViewSize: AdViewSize = AdType.Banner.Standard.size
}

class MRECProgrammaticFragment: BannerProgrammaticFragment() {

    override fun logTagFilterRule(logTag: String, forTag: String): String? =
        commonLogTagListRules(forTag) ?: when (forTag) {
            "CX:$logTag", "CX:BannerManager" -> "MREC"
            else -> null
        }

    override fun createAdView(
        activity: Activity, placementName: String, listener: CloudXAdViewListener?
    ): CloudXAdView? = CloudX.createMREC(activity, placementName, listener)

    override val adViewSize: AdViewSize = AdType.Banner.MREC.size
}

class NativeAdSmallProgrammaticFragment: BannerProgrammaticFragment() {

    override fun logTagFilterRule(logTag: String, forTag: String): String? =
        commonLogTagListRules(forTag) ?: when (forTag) {
            "CX:$logTag", "CX:BannerManager" -> "NativeAdSmall"
            else -> null
        }

    override fun createAdView(
        activity: Activity, placementName: String, listener: CloudXAdViewListener?
    ): CloudXAdView? =
        CloudX.createNativeAdSmall(activity, placementName, listener)

    override val adViewSize: AdViewSize = AdType.Native.Small.size
}

class NativeAdMediumProgrammaticFragment: BannerProgrammaticFragment() {

    override fun logTagFilterRule(logTag: String, forTag: String): String? =
        commonLogTagListRules(forTag) ?: when (forTag) {
            "CX:$logTag", "CX:BannerManager" -> "NativeAdMedium"
            else -> null
        }

    override fun createAdView(
        activity: Activity, placementName: String, listener: CloudXAdViewListener?
    ): CloudXAdView? =
        CloudX.createNativeAdMedium(activity, placementName, listener)

    override val adViewSize: AdViewSize = AdType.Native.Medium.size
}