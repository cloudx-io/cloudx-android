package io.cloudx.sdk.internal.ads.banner.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Merges the banner's own visibility with app foreground to produce "effective visibility".
 * If your upstream already emits false on background, this is still harmless.
 */
internal class EffectiveVisibilityGate(
    bannerVisibility: StateFlow<Boolean>,
    appForeground: StateFlow<Boolean>, // wrap AppLifecycleService if needed
    scope: CoroutineScope
) {
    private val _effective = MutableStateFlow(false)
    val effective: StateFlow<Boolean> = _effective

    init {
        scope.launch {
            combine(bannerVisibility, appForeground) { banner, app ->
                banner && app
            }.collect { _effective.value = it }
        }
    }
}
