package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdError

interface AdLoadStatusListener {
    fun onIsAdLoadedStatusChanged(isAdLoaded: Boolean)
}

class AdLoadStatusListenerWrapper(
    private val wrappedListener: CloudXAdListener,
    private val statusListener: AdLoadStatusListener
) : CloudXAdListener {

    private var isAdLoaded = false

    override fun onAdLoaded(cloudXAd: CloudXAd) {
        wrappedListener.onAdLoaded(cloudXAd)
        updateAdLoadedStatus(true)
    }

    override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
        wrappedListener.onAdLoadFailed(cloudXAdError)
        updateAdLoadedStatus(false)
    }

    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        wrappedListener.onAdDisplayed(cloudXAd)
    }

    override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
        wrappedListener.onAdDisplayFailed(cloudXAdError)
        updateAdLoadedStatus(false)
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        wrappedListener.onAdHidden(cloudXAd)
        updateAdLoadedStatus(false)
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        wrappedListener.onAdClicked(cloudXAd)
    }

    private fun updateAdLoadedStatus(newStatus: Boolean) {
        if (isAdLoaded != newStatus) {
            isAdLoaded = newStatus
            statusListener.onIsAdLoadedStatusChanged(isAdLoaded)
        }
    }
}