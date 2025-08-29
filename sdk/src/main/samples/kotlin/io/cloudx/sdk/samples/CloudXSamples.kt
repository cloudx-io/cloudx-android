package io.cloudx.sdk.samples

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import io.cloudx.sdk.CloudXAdViewListener
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXAd
import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXInterstitialAd
import io.cloudx.sdk.CloudXIsAdLoadedListener
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.CloudXRewardedAd
import io.cloudx.sdk.CloudXInterstitialListener
import io.cloudx.sdk.RewardedInterstitialListener

internal fun cloudXSetPrivacy() {
    CloudX.setPrivacy(
        CloudXPrivacy(
            isUserConsent = true, // user gave consent (GDPR)
            isAgeRestrictedUser = null, // null, flag is not set (COPPA).
            isDoNotSell = true // do not sell my data (CCPA)
        )
    )
}

internal fun cloudXInitialize(activity: Activity) {
    // Consider updating privacy values first.
    // CloudX.setPrivacy(CloudXPrivacy(...))

    CloudX.initialize(
        activity,
        CloudX.InitializationParams(
            appKey = "app_key",
            initEndpointUrl = "init_endpoint_url",
            hashedUserId = null
        )
    ) { initStatus ->

        if (initStatus.initialized) {
            // SDK is initialized; now you can create ad instances.
        } else {
            // Initialization failed.
            Log.d("SDK", "SDK init failed: ${initStatus.description}")
        }
    }
}

internal fun cloudXCreateAdView(activity: Activity, frameLayout: FrameLayout) {
    // Alternatives, depending on your needs:
    // CloudX.createMREC(...)
    // CloudX.createNativeAdSmall(...)
    // CloudX.createNativeAdMedium(...)
    val adView = CloudX.createBanner(
        activity,
        placementName = "CLOUDX_PLACEMENT_NAME",
        // Track events if necessary.
        listener = object : CloudXAdViewListener {
            override fun onAdLoaded(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
                // ..
            }

            override fun onAdDisplayed(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
                // ..
            }

            override fun onAdHidden(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdClicked(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdExpanded(placementName: String) {
                // ..
            }

            override fun onAdCollapsed(placementName: String) {
                // ..
            }
        }
    )

    // Alternatively, you can set listener anytime.
    adView?.listener = null

    if (adView != null) {
        // Once ad view is added to the view hierarchy,
        // ads will start load and display automatically (depends on init config refresh settings)
        frameLayout.addView(adView)

        // Release ad view resources, once it's not needed anymore
        // or activity/fragment it is attached to is destroyed.
        adView.destroy()
        frameLayout.removeView(adView)
    } else {
        Log.d("SDK", "SDK not initialized or placement does not exist")
    }
}

internal fun createInterstitial(activity: Activity, placementName: String) {
    var ad: CloudXInterstitialAd? = null
    ad = CloudX.createInterstitial(
        activity,
        placementName,
        // Track events if necessary.
        object : CloudXInterstitialListener {
            override fun onAdLoaded(cloudXAd: CloudXAd) {
                // ..
                // Ad is loaded, now you can show ad.
                // Operation result will be returned in onAdShowSuccess() or onAdShowFailed() callbacks.
                ad?.show()
            }

            override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
                // ..
            }

            override fun onAdDisplayed(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
                // ..
                // Ad failed to show due to internal error or it hasn't loaded yet.
            }

            override fun onAdHidden(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdClicked(cloudXAd: CloudXAd) {
                // ..
            }
        }
    )

    if (ad != null) {
        // Whenever you need to check or await ad "load" status:
        // Result will be returned in onAdLoadSuccess() or onAdLoadFailed() callbacks
        ad.load()

        // Alternatively, ad load state can be checked on demand:
        if (ad.isAdLoaded) {
            // Operation result will be returned in onAdShowSuccess() or onAdShowFailed() callbacks
            ad.show()
        }

        // Or by setting isAdLoaded listener:
        ad.setIsAdLoadedListener(object : CloudXIsAdLoadedListener {
            override fun onIsAdLoadedStatusChanged(isAdLoaded: Boolean) {
                if (isAdLoaded) {
                    ad.show()
                }
            }
        })
    } else {
        Log.d("SDK", "SDK not initialized or placement does not exist")
    }

    // Release ad view resources, once it's not needed anymore
    // or activity/fragment it is attached to is destroyed.
    ad?.destroy()
}

internal fun createRewarded(activity: Activity, placementName: String) {
    var ad: CloudXRewardedAd? = null
    ad = CloudX.createRewardedInterstitial(
        activity,
        placementName,
        // Track events if necessary.
        object : RewardedInterstitialListener {
            override fun onUserRewarded(cloudXAd: CloudXAd) {
                // Track ad reward here.
            }

            override fun onAdLoaded(cloudXAd: CloudXAd) {
                // ..
                // Ad is loaded, now you can show ad.
                // Operation result will be returned in onAdShowSuccess() or onAdShowFailed() callbacks.
                ad?.show()
            }

            override fun onAdLoadFailed(cloudXAdError: CloudXAdError) {
                // ..
            }

            override fun onAdDisplayed(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdDisplayFailed(cloudXAdError: CloudXAdError) {
                // ..
                // Ad failed to show due to internal error or it hasn't loaded yet.
            }

            override fun onAdHidden(cloudXAd: CloudXAd) {
                // ..
            }

            override fun onAdClicked(cloudXAd: CloudXAd) {
                // ..
            }
        }
    )

    if (ad != null) {
        // Whenever you need to check or await ad "load" status:
        // Result will be returned in onAdLoadSuccess() or onAdLoadFailed() callbacks
        ad.load()

        // Alternatively, ad load state can be checked on demand:
        if (ad.isAdLoaded) {
            // Operation result will be returned in onAdShowSuccess() or onAdShowFailed() callbacks
            ad.show()
        }

        // Or by setting isAdLoaded listener:
        ad.setIsAdLoadedListener(object : CloudXIsAdLoadedListener {
            override fun onIsAdLoadedStatusChanged(isAdLoaded: Boolean) {
                if (isAdLoaded) {
                    ad.show()
                }
            }
        })
    } else {
        Log.d("SDK", "SDK not initialized or placement does not exist")
    }

    // Release ad view resources, once it's not needed anymore
    // or activity/fragment it is attached to is destroyed.
    ad?.destroy()
}