package io.cloudx.adapter.meta

import com.facebook.ads.AdSettings

fun enableMetaAudienceNetworkTestMode(enableTestMode: Boolean) {
    AdSettings.setTestMode(enableTestMode)
}