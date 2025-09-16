package io.cloudx.demo.demoapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import io.cloudx.demo.demoapp.loglistview.commonLogTagListRules
import io.cloudx.demo.demoapp.loglistview.setupLogListView
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXFullscreenAd
import io.cloudx.sdk.CloudXIsAdLoadedListener
import io.cloudx.sdk.internal.CXLogger

abstract class FullPageAdFragment : Fragment(R.layout.fragment_fullscreen_ad) {

    private lateinit var loadButton: Button
    private lateinit var showButton: Button

    private var ad: CloudXFullscreenAd? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(view) {
            loadButton = findViewById(R.id.btn_load)
            loadButton.setOnClickListener { onLoadClick() }

            showButton = findViewById(R.id.btn_show)
            showButton.setOnClickListener { onShowClick() }
            showButton.textColor(enabled = false)
        }

        // Creates ad if null once SDK is initialized.
        viewLifecycleOwner.repeatOnStart {
            if (ad != null) return@repeatOnStart
            ad = createAd(LoggedCloudXAdListener(logTag, placementName))
            if (ad == null) {
                CXLogger.e(
                    logTag,
                    "can't create $adType ad: $placementName placement is missing in SDK config"
                )
            } else {
                CXLogger.i(
                    logTag,
                    "$adType created for $placementName placement"
                )
            }

            ad?.setIsAdLoadedListener(isAdReadyListener)
        }

        setupLogListView(view.findViewById(R.id.log_list), ::logTagRule)
    }

    override fun onDestroy() {
        super.onDestroy()
        ad?.destroy()
    }

    private val isAdReadyListener = object : CloudXIsAdLoadedListener {
        override fun onIsAdLoadedStatusChanged(isAdLoaded: Boolean) {
            showButton.textColor(isAdLoaded)
        }
    }

    // TODO. Quick workaround to support both int and rew ads + all their callbacks.
    abstract fun createAd(listener: CloudXAdListener): CloudXFullscreenAd?

    protected val placementName: String by lazy {
        requireArguments().getPlacements().firstOrNull() ?: ""
    }

    abstract val adType: String
    abstract val logTag: String
    open fun logTagRule(forTag: String): String? =
        commonLogTagListRules(forTag) ?: when (forTag) {
            "CX:$logTag" -> adType
            else -> null
        }

    private fun onLoadClick() {
        val ad = this.ad
        if (ad == null) {
            CXLogger.e(
                logTag,
                "Can't load: $adType ad wasn't created for placement: $placementName;"
            )
            return
        }

        ad.load()
    }

    private fun onShowClick() {
        val ad = this.ad
        if (ad == null) {
            CXLogger.e(
                logTag,
                "Can't show: $adType ad wasn't created for placement: $placementName;"
            )
            return
        }

        ad.show()
    }

    companion object {

        fun createArgs(
            placementName: ArrayList<String>,
        ): Bundle = Bundle().apply {
            putPlacements(placementName)
        }
    }
}

private fun Button.textColor(enabled: Boolean) {
    setTextColor(
        MaterialColors.getColor(
            this,
            if (enabled) {
                com.google.android.material.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOutline
            }
        )
    )
}