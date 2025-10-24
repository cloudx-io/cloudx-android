package io.cloudx.sdk.internal.tracker.metrics

sealed class MetricsType(val typeCode: String) {
    sealed class Network(typeCode: String) : MetricsType(typeCode) {
        object SdkInit : Network("network_call_sdk_init_req")
        object GeoApi : Network("network_call_geo_req")
        object BidRequest : Network("network_call_bid_req")
    }

    sealed class Method(typeCode: String) : MetricsType(typeCode) {
        object SdkInitMethod : Method("method_sdk_init")
        object CreateBanner : Method("method_create_banner")
        object CreateInterstitial : Method("method_create_interstitial")
        object CreateRewarded : Method("method_create_rewarded")
        object CreateMrec : Method("method_create_mrec")
        object CreateNative : Method("method_create_native")
        object SetHashedUserId : Method("method_set_hashed_user_id")
        object SetUserKeyValues : Method("method_set_user_key_values")
        object SetAppKeyValues : Method("method_set_app_key_values")
        object BannerRefresh : Method("method_banner_refresh")
    }
}
