package io.cloudx.sdk.internal.core.ad

import io.cloudx.sdk.internal.AdNetwork

internal interface AdMetaData {

    val price: Double?
    val adNetwork: AdNetwork
    val placementId: String
}