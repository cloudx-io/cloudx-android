package io.cloudx.adapter.meta

import io.cloudx.sdk.internal.CloudXLogger

internal fun log(tag: String, message: String) {
    CloudXLogger.debug(tag, message)
}
