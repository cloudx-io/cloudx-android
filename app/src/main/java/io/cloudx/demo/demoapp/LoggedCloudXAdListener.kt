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
            "Load Success; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }

    override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
        CXLogger.i(logTag, "LOAD FAILED; placement: $placementName;")
    }

    override fun onAdDisplayed(cloudXAd: CloudXAd) {
//        CloudXLogger.info(
//            logTag,
//            "Ad shown — placement: $placementName, network: ${cloudXAd.networkName}"
//        )
    }

    override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
        CXLogger.i(logTag, "SHOW FAILED; placement: $placementName;")
    }

    override fun onAdHidden(cloudXAd: CloudXAd) {
//        CloudXLogger.info(
//            logTag,
//            "Ad hidden; placement: $placementName; network: ${cloudXAd.networkName}"
//        )
    }

    override fun onAdClicked(cloudXAd: CloudXAd) {
        CXLogger.i(
            logTag,
            "Ad clicked; placement: $placementName; network: ${cloudXAd.bidderName}"
        )
    }
}