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

// Use this approach to get/listen to real state of visibility of the view.
// It means, it checks whether activity is resumed, view isShows, view's area is on-the-screen.
// Feel free to improve/extend/optimize it.

internal interface ViewabilityTracker : Destroyable {

    val isViewable: StateFlow<Boolean>
}

private class ViewabilityTrackerImpl(
    private val view: View,
    scope: CoroutineScope,
    // I have to use this due to absence of publicly available apis for that.
    isViewShown: StateFlow<Boolean>,
) : ViewabilityTracker {

    private val scope = scope + Dispatchers.Main

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_PAUSE -> isLifecycleResumed.value = false
            Lifecycle.Event.ON_RESUME -> isLifecycleResumed.value = true
            else -> {
                // Nothing to see here..
            }
        }
    }

    // Null - lifecycle tracking is unavailable (because unity uses Activity instead of Compat Activity).
    private val isLifecycleResumed = MutableStateFlow<Boolean?>(null)

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

    private val isAttached = MutableStateFlow(view.isAttachedToWindow)

    private val lifecycleOwnerUpdateJob = isAttached.onEach {
        currentLifecycleOwner = if (it) view.findViewTreeLifecycleOwner() else null
    }.launchIn(scope)

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

    private val isEnoughAreaVisible = MutableStateFlow(isEnoughAreaVisible())

    private var layoutRecalculationJob: Job? = null

    private fun recalculateIsEnoughAreaVisible() {
        // TODO. Profile.
        layoutRecalculationJob?.cancel()
        layoutRecalculationJob = scope.launch {
            delay(500)
            // At least 50% of the view area should be visible.
            isEnoughAreaVisible.value = isEnoughAreaVisible()
        }
    }

    // TODO. For optimization purposes.
    private val globalVisibleRect: Rect = Rect(0, 0, 0, 0)

    private fun isEnoughAreaVisible(): Boolean {
        if (!view.isShown) return false
        val totalW = view.width
        val totalH = view.height
        if (totalW <= 0 || totalH <= 0) return false

        val anyVisible = view.getGlobalVisibleRect(globalVisibleRect)
        if (!anyVisible) return false

        val visibleArea = (globalVisibleRect.width().coerceAtLeast(0)) * (globalVisibleRect.height().coerceAtLeast(0))
        val totalArea = totalW * totalH
        if (totalArea <= 0) return false

        val ratio = visibleArea.toDouble() / totalArea.toDouble()
        return ratio >= 0.5
    }

    // TODO. Ideally, I should calculate it here correctly,
    //  but it doesn't really matter in the use-case.
    private var _isViewable = MutableStateFlow(false)

    override val isViewable: StateFlow<Boolean> = _isViewable

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
}

internal fun View.createViewabilityTracker(
    scope: CoroutineScope,
    // I have to use this due to absence of publicly available apis for that.
    isViewShown: StateFlow<Boolean>,
): ViewabilityTracker =
    ViewabilityTrackerImpl(this, scope, isViewShown)
