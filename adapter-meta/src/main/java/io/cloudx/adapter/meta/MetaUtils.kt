package io.cloudx.adapter.meta

import android.os.Bundle
import io.cloudx.sdk.internal.CloudXLogger

internal fun log(tag: String, message: String, throwable: Throwable? = null) {
    CloudXLogger.d(tag, message, throwable)
}

internal fun Bundle.getPlacementIds(): List<String> =
    getStringArray("placementIds")?.toList() ?: emptyList()

internal fun Bundle.getPlacementId(): String? = getString("placement_id")
