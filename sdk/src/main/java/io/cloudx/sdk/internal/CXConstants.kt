package io.cloudx.sdk.internal

/**
 * Network
 */
internal const val HEADER_CLOUDX_STATUS = "X-CloudX-Status"
internal const val STATUS_SDK_DISABLED = "SDK_DISABLED"
internal const val STATUS_ADS_DISABLED = "ADS_DISABLED"

internal const val CLOUDX_DEFAULT_RETRY_MS = 1000L

/**
 * Bid prices
 */
/** Sentinel value indicating an unknown or unavailable bid price */
internal const val UNKNOWN_BID_PRICE = -1f