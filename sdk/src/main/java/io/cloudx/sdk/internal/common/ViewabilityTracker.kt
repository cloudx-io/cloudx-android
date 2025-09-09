package io.cloudx.sdk.internal.common

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.cloudx.sdk.Destroyable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * ViewabilityTracker provides comprehensive view visibility tracking by monitoring:
 * - Activity lifecycle state (resumed/paused)
 * - View attachment to window
 * - View's actual visible area on screen (any visible pixels)
 * - Custom view shown state from external sources
 * 
 * A view is considered "viewable" when ALL conditions are met:
 * - The containing activity is resumed (or lifecycle unavailable for Unity)
 * - The view is attached to the window
 * - At least one pixel of the view is visible on screen (uses getGlobalVisibleRect)
 * - The external isViewShown state is true
 */
internal interface ViewabilityTracker : Destroyable {
    /**
     * StateFlow that emits true when the view meets all viewability criteria
     */
    val isViewable: StateFlow<Boolean>
}

private class ViewabilityTrackerImpl(
    private val view: View,
    scope: CoroutineScope,
    // I have to use this due to absence of publicly available apis for that.
    isViewShown: StateFlow<Boolean>,
) : ViewabilityTracker {

    private val scope = scope + Dispatchers.Main

    // State tracking properties
    private val isAttached = MutableStateFlow(view.isAttachedToWindow)
    private val isLifecycleResumed =
        MutableStateFlow<Boolean?>(null) // Null - lifecycle tracking is unavailable (because unity uses Activity instead of Compat Activity)
    private val isEnoughAreaVisible = MutableStateFlow(isEnoughAreaVisible())
    private val _isViewable = MutableStateFlow(false)

    // Job tracking properties
    private var layoutRecalculationJob: Job? = null
    private val lifecycleOwnerUpdateJob = isAttached.onEach {
        currentLifecycleOwner = if (it) view.findViewTreeLifecycleOwner() else null
    }.launchIn(scope)
    // TODO. Profile, optimize if needed.
    private val isViewableJob = isViewShown.combine(isAttached) { isViewShown, isAttached ->
        isViewShown && isAttached
    }.combine(isEnoughAreaVisible) { prevResult, isEnoughAreaVisible ->
        prevResult && isEnoughAreaVisible
    }.combine(isLifecycleResumed) { prevResult, isLifecycleResumed ->
        // Pass if lifecycle state is at least RESUMED or lifecycle data absent (99% Unity case).
        prevResult && isLifecycleResumed != false
    }.onEach {
        _isViewable.value = it
    }.launchIn(scope)

    // Lifecycle management
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> isLifecycleResumed.value = false
            Lifecycle.Event.ON_RESUME -> isLifecycleResumed.value = true
            else -> {
                // Nothing to see here..
            }
        }
    }
    private var currentLifecycleOwner: LifecycleOwner? = null
        set(value) {
            val oldLifecycleOwner = field
            val newLifecycleOwner = value

            if (oldLifecycleOwner == newLifecycleOwner) return

            field = newLifecycleOwner

            oldLifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
            // null - lifecycle state is unavailable.
            isLifecycleResumed.value = null

            newLifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        }

    // View listeners
    private val onWindowAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(p0: View) {
            isAttached.value = true
        }

        override fun onViewDetachedFromWindow(p0: View) {
            isAttached.value = false
        }
    }.also {
        view.addOnAttachStateChangeListener(it)
    }
    private val onScrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        recalculateIsEnoughAreaVisible()
    }.also {
        view.viewTreeObserver.addOnScrollChangedListener(it)
    }
    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        recalculateIsEnoughAreaVisible()
    }.also {
        view.viewTreeObserver.addOnGlobalLayoutListener(it)
    }

    // Utility properties
    // TODO. For optimization purposes.
    private val globalVisibleRect: Rect = Rect(0, 0, 0, 0)

    // Public API
    override val isViewable: StateFlow<Boolean> = _isViewable

    override fun destroy() {
        // Scope should be cancelled externally since it came from outside.

        view.removeOnAttachStateChangeListener(onWindowAttachListener)

        with(view.viewTreeObserver) {
            removeOnGlobalLayoutListener(onGlobalLayoutListener)
            removeOnScrollChangedListener(onScrollChangedListener)
        }

        currentLifecycleOwner = null

        isViewableJob.cancel()
        lifecycleOwnerUpdateJob.cancel()
        layoutRecalculationJob?.cancel()
    }

    // Private methods
    private fun recalculateIsEnoughAreaVisible() {
        // TODO. Profile.
        layoutRecalculationJob?.cancel()
        layoutRecalculationJob = scope.launch {
            delay(500) // Debounce rapid layout/scroll changes for performance
            // Check if any pixels of the view are visible on screen
            isEnoughAreaVisible.value = isEnoughAreaVisible()
        }
    }

    /**
     * Returns true if any pixels of the view are visible on screen.
     * Uses Android's getGlobalVisibleRect which returns true even if only 1 pixel is visible.
     */
    private fun isEnoughAreaVisible(): Boolean = view.getGlobalVisibleRect(globalVisibleRect)
}

internal fun View.createViewabilityTracker(
    scope: CoroutineScope,
    // I have to use this due to absence of publicly available apis for that.
    isViewShown: StateFlow<Boolean>,
): ViewabilityTracker =
    ViewabilityTrackerImpl(this, scope, isViewShown)