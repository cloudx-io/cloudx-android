package io.cloudx.sdk.internal.bid

import io.cloudx.sdk.internal.core.ad.adapter_delegate.BannerAdapterDelegate

internal data class LoadResult(val banner: BannerAdapterDelegate?, val lossReason: LossReason?)
