package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.Destroyable

internal sealed class BidSourceResult<T : Destroyable> {
    data class Success<T : Destroyable>(val response: BidAdSourceResponse<T>) : BidSourceResult<T>()
}
