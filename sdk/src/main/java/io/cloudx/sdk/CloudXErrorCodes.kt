package io.cloudx.sdk

object CloudXErrorCodes {
    const val INIT_SDK_DISABLED = 105        // init kill switch
    const val ADS_DISABLED = 308             // individual bid kill switch

    const val HYPOTHETICAL_NO_ADS_LOADED_YET = 450
    const val HYPOTHETICAL_AD_ALREADY_DISPLAYING = 451

    const val NO_FILL = 300 // ✅
    
    // Error codes below are now updated to match the CloudX specification.
    const val TIMEOUT = 408
    const val NETWORK_ERROR = 503
    const val SERVER_ERROR = 500
    const val CLIENT_ERROR = 400
    const val RATE_LIMITED = 429
    const val UNKNOWN_ERROR = 520
    const val NO_BID_AVAILABLE = 204
}
