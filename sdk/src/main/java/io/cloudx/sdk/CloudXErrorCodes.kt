package io.cloudx.sdk

object CloudXErrorCodes {
    const val INIT_SDK_DISABLED = 105        // init kill switch
    const val ADS_DISABLED = 308             // individual bid kill switch

    const val HYPOTHETICAL_NO_ADS_LOADED_YET = 450
    const val HYPOTHETICAL_AD_ALREADY_DISPLAYING = 451

    const val NO_FILL = 300 // ✅
    
    // TODO: update codes with the actual values shown in the spec
    const val TIMEOUT = 1001
    const val NETWORK_ERROR = 1002
    const val SERVER_ERROR = 1003
    const val CLIENT_ERROR = 1004
    const val RATE_LIMITED = 1005
    const val UNKNOWN_ERROR = 1006
    const val NO_BID_AVAILABLE = 1007
}
