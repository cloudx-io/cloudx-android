package io.cloudx.adapter.meta

import android.os.Bundle
import com.facebook.ads.AdError
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXErrorCode
import io.cloudx.sdk.toCloudXError

internal fun Bundle.getMetaPlacementIds(): List<String> =
    getStringArray("placementIds")?.toList() ?: emptyList()

internal fun Bundle.getMetaPlacementId(): String? = getString("placement_id")

internal fun AdError?.toCloudXError(): CloudXError {
    return when (this?.errorCode) {
        AdError.NETWORK_ERROR_CODE -> CloudXErrorCode.ADAPTER_NO_CONNECTION
        AdError.NO_FILL_ERROR_CODE -> CloudXErrorCode.ADAPTER_NO_FILL
        AdError.SERVER_ERROR_CODE,
        AdError.REMOTE_ADS_SERVICE_ERROR -> CloudXErrorCode.ADAPTER_SERVER_ERROR

        AdError.INTERNAL_ERROR_CODE,
        AdError.INTERSTITIAL_AD_TIMEOUT -> CloudXErrorCode.ADAPTER_TIMEOUT

        AdError.CACHE_ERROR_CODE,
        AdError.BROKEN_MEDIA_ERROR_CODE,
        AdError.SHOW_CALLED_BEFORE_LOAD_ERROR_CODE,
        AdError.LOAD_CALLED_WHILE_SHOWING_AD,
        AdError.LOAD_TOO_FREQUENTLY_ERROR_CODE,
        AdError.NATIVE_AD_IS_NOT_LOADED,
        AdError.INCORRECT_STATE_ERROR -> CloudXErrorCode.ADAPTER_INVALID_LOAD_STATE

        AdError.CLEAR_TEXT_SUPPORT_NOT_ALLOWED -> CloudXErrorCode.ADAPTER_INVALID_CONFIGURATION

        else -> CloudXErrorCode.ADAPTER_UNEXPECTED_ERROR
    }.toCloudXError(message = this?.errorMessage)
}
