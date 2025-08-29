package io.cloudx.sdk.internal.core.ad.source.bid

import io.cloudx.sdk.Destroyable

// New: a domain error with enough shape for the banner to decide what to do
internal data class BidSourceError(
    val message: String,
    val code: Int,
    val isPermanent: Boolean = false,       // true for 4xx (except 429)
    val isTrafficControl: Boolean = false   // true for ADS_DISABLED (308)
)

// New: never nullable; always one of these
internal sealed class BidSourceResult<T : Destroyable> {
    data class Success<T : Destroyable>(val response: BidAdSourceResponse<T>) : BidSourceResult<T>()
    data class Failure<T : Destroyable>(val error: BidSourceError) : BidSourceResult<T>()
}
