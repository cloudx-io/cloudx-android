package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.internal.CXLogger

class LoggedCloudXAdListener(
    private val logTag: String,
    private val placementName: String
) : CloudXAdListener {

    override fun onAdLoaded(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Load success; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }

    override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
        CXLogger.i(
            logTag,
            "Load failed; placement: $placementName; error: ${cloudXAdError.description}"
        )
    }

    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Ad displayed; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }

    override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
        CXLogger.i(
            logTag,
            "Display failed; placement: $placementName; error: ${cloudXAdError.description}"
        )
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Ad hidden; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Ad clicked; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }
}