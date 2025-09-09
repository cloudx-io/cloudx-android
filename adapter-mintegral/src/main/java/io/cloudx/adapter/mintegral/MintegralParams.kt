package io.cloudx.adapter.mintegral

import android.os.Bundle

internal fun Bundle.placementId(): String? = getString("placement_id")
internal fun Bundle.bidId(): String? = getString("bid_id")