package io.cloudx.adapter.meta

import android.os.Bundle

internal fun Bundle.getPlacementIds(): List<String> =
    getStringArray("placementIds")?.toList() ?: emptyList()

internal fun Bundle.getPlacementId(): String? = getString("placement_id")
