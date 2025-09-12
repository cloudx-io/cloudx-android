package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.Destroyable

// Bid-level policy outcomes the banner cares about
internal sealed class BidSourceResult<T : Destroyable> {
    data class Success<T : Destroyable>(val response: BidAdSourceResponse<T>) : BidSourceResult<T>()
    class NoFill<T : Destroyable> : BidSourceResult<T>()
    data class TransientFailure<T : Destroyable>(val message: String) : BidSourceResult<T>()
    data class TrafficControl<T : Destroyable>(val message: String) : BidSourceResult<T>()
}
