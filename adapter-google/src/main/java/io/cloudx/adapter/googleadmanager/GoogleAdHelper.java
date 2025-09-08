package io.cloudx.adapter.googleadmanager;

import android.app.Activity;

import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;

/**
 * Helper class to bypass Kotlin nullability constraints when calling Google Ad APIs
 * Google ads will still successfully show even with a null Activity
 */
public class GoogleAdHelper {

    public static void showInterstitial(AdManagerInterstitialAd ad, Activity activity) {
        ad.show(activity);
    }

    public static void showRewardedInterstitial(RewardedInterstitialAd ad, Activity activity, OnUserEarnedRewardListener listener) {
        ad.show(activity, listener);
    }
}