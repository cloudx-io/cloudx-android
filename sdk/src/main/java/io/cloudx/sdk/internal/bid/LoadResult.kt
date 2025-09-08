package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.internal.ads.banner.BannerAdapterDelegate

internal data class LoadResult(val banner: BannerAdapterDelegate?, val lossReason: LossReason?)
