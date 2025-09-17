package io.cloudx.sdk.internal.ads

import io.cloudx.sdk.CloudXError
import kotlinx.coroutines.flow.StateFlow

interface LastErrorEvent {

    val lastErrorEvent: StateFlow<CloudXError?>
}