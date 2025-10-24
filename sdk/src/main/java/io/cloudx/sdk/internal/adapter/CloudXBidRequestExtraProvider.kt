package io.cloudx.sdk.internal.adapter

import android.content.Context

/**
 * Bid request extra provider: some ad networks require an additional data to be passed into Bid Request for running ortb auction,
 * for example, bid token. such data can be passed via implementing this interface.
 */
interface CloudXAdapterBidRequestExtrasProvider {

    suspend operator fun invoke(context: Context): Map<String, String>?
}