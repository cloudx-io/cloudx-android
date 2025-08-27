package io.cloudx.sdk

object CloudXErrorCodes {
    const val INIT_SDK_DISABLED = 105        // init kill switch
    const val ADS_DISABLED = 308             // individual bid kill switch

    const val HYPOTHETICAL_NO_ADS_LOADED_YET = 450
    const val HYPOTHETICAL_AD_ALREADY_DISPLAYING = 451
}
