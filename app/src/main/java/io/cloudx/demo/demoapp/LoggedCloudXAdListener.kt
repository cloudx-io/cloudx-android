package io.cloudx.demo.demoapp

import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdListener
import io.cloudx.sdk.CloudXError
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

    override fun onAdLoadFailed(cloudXError: CloudXError) {
        CXLogger.e(
            logTag,
            "Load failed; placement: $placementName; error: ${cloudXError.effectiveMessage}"
        )
    }

    override fun onAdDisplayed(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Ad displayed; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }

    override fun onAdDisplayFailed(cloudXError: CloudXError) {
        CXLogger.e(
            logTag,
            "Display failed; placement: $placementName; error: ${cloudXError.effectiveMessage}"
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