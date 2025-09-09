# Banner Refresh Cadence – Problem, Code, and Fix

This document explains the banner refresh timer issue relative to the MVP, shows the relevant code, and proposes a fix.

## MVP Excerpts (what we’re aligning to)

- “Wait to restart the timer until the bid request either succeeds or fails”
- “This avoids stacking requests.”
- “On failure, emit error and wait for the next interval (no banner-level retry).”
- “The refresh interval is wall-clock and continues while hidden”
- “If it elapses while hidden, queue exactly one bid request for when it becomes visible again”

## Problem Statement

- The banner refresh cadence does not match the MVP “no stacking” rule during in‑flight requests. The clock free‑runs and queues a tick while loading; when the load finishes, it fires immediately, creating back‑to‑back requests if the prior load took longer than the interval.

## Why It Matters

- Back-to-back requests after long loads increase burstiness and deviate from the pacing defined in the MVP. This can impact server load, pacing metrics, and device resources.

## Current Behavior (Summary)

- Wall clock runs continuously (good for hidden behavior).
- If the interval elapses while a request is in-flight, the clock queues an in-flight tick.
- When the request finishes, that queued tick fires immediately if visible → next request starts with no cool-down.
- Hidden behavior is correct: exactly one tick is queued while hidden and fires on next visible.
- Transient failure handling has been fixed to emit an error and continue (no teardown).

## MVP Expectation (Summary)

- When a request completes (success or failure), restart the countdown to a full `refreshSeconds` before launching the next request (avoid stacking).
- Preserve wall-clock semantics while hidden: if hidden and not in-flight, let time pass and queue exactly one request for when visible.

---

## Relevant Code (Today)

1) Visibility-aware cadence clock

File: `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/components/CadenceClock.kt`

```kotlin
internal class VisibilityAwareOneQueuedClock(
    private val intervalMs: Long,
    private val scope: CoroutineScope
) : CadenceClock {

    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val ticks: SharedFlow<Unit> = _ticks.asSharedFlow()

    private var job: Job? = null
    private var inflight = false
    private var visible = false
    private var queuedInFlight = false
    private var queuedHidden = false

    override fun start() {
        if (job != null) return
        require(intervalMs >= 1000) { "Refresh interval must be >= 1s" }
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                when {
                    inflight -> queuedInFlight = true
                    !visible -> queuedHidden = true
                    else -> _ticks.tryEmit(Unit)
                }
            }
        }
        // Optional: immediate tick if already visible
        if (visible) _ticks.tryEmit(Unit) else queuedHidden = true
    }

    override fun stop() {
        job?.cancel()
        job = null
        inflight = false
        queuedInFlight = false
        queuedHidden = false
    }

    override fun setVisible(v: Boolean) {
        val wasVisible = visible
        visible = v
        if (visible && !wasVisible && !inflight && queuedHidden) {
            queuedHidden = false
            _ticks.tryEmit(Unit)
        }
    }

    override fun markRequestStarted() {
        inflight = true
    }

    override fun markRequestFinished() {
        inflight = false
        if (visible && queuedInFlight) {
            queuedInFlight = false
            _ticks.tryEmit(Unit)
        }
        // if hidden we’ll emit when we become visible via setVisible()
    }
}
```

2) How the manager uses the clock

File: `sdk/src/main/java/io/cloudx/sdk/internal/ads/banner/BannerManagerImpl.kt`

```kotlin
private val clock = VisibilityAwareOneQueuedClock(
    intervalMs = (refreshSeconds.coerceAtLeast(1) * 1000L),
    scope = scope
)

init {
    start()
}

private fun start() {
    // react to effective visibility
    visJob?.cancel()
    visJob = scope.launch {
        gate.effective.collect { visible ->
            clock.setVisible(visible)
            if (visible) {
                prefetched?.let { presenter.show(it); prefetched = null }
            }
        }
    }

    clock.start()

    tickJob?.cancel()
    tickJob = scope.launch {
        clock.ticks.collect { launchRequest() }
    }
}

private fun launchRequest() {
    clock.markRequestStarted()
    reqJob?.cancel()
    reqJob = scope.launch {
        // ... await connection
        when (val outcome = loader.loadOnce()) {
            is BannerLoadOutcome.Success -> {
                if (gate.effective.value) presenter.show(outcome.banner)
                else { prefetched?.destroy(); prefetched = outcome.banner }
            }
            BannerLoadOutcome.NoFill -> listener?.onAdLoadFailed(...)
            BannerLoadOutcome.TransientFailure -> listener?.onAdLoadFailed(...)
            BannerLoadOutcome.PermanentFailure -> { stopPermanently(...); return@launch }
            BannerLoadOutcome.TrafficControl -> listener?.onAdLoadFailed(...)
        }
        clock.markRequestFinished()
    }
}
```

---

## Proposed Fix (Design)

- Do not queue a tick during in-flight (no `queuedInFlight`).
- When a request finishes, restart the cadence to wait a full `intervalMs` before the next tick.
- Preserve current hidden behavior: while hidden, queue exactly one; when becoming visible and not in-flight, emit that queued-hidden tick immediately.

### Suggested Implementation

Replace the clock logic with a “restartable” loop:
- `markRequestFinished()` restarts the delay window (cancels and recreates the job).
- The loop does not set a queued-in-flight tick; instead it ignores elapsed ticks during in-flight.
- `setVisible(true)` still emits the queued-hidden tick immediately when not in-flight.

Code (drop-in replacement for `VisibilityAwareOneQueuedClock`):

```kotlin
internal class VisibilityAwareOneQueuedClock(
    private val intervalMs: Long,
    private val scope: CoroutineScope
) : CadenceClock {

    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val ticks: SharedFlow<Unit> = _ticks.asSharedFlow()

    private var job: Job? = null
    private var inflight = false
    private var visible = false
    private var queuedHidden = false

    override fun start() {
        if (job != null) return
        require(intervalMs >= 1000) { "Refresh interval must be >= 1s" }
        // Immediate first tick if already visible, else queue hidden
        if (visible) _ticks.tryEmit(Unit) else queuedHidden = true
        scheduleLoop()
    }

    override fun stop() {
        job?.cancel(); job = null
        inflight = false
        queuedHidden = false
    }

    override fun setVisible(v: Boolean) {
        val wasVisible = visible
        visible = v
        if (visible && !wasVisible && !inflight && queuedHidden) {
            queuedHidden = false
            _ticks.tryEmit(Unit)
        }
    }

    override fun markRequestStarted() {
        inflight = true
    }

    override fun markRequestFinished() {
        inflight = false
        // Restart the cadence: next tick happens after a full interval from now
        scheduleLoop()
    }

    private fun scheduleLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                when {
                    inflight -> {/* ignore elapsed time while in-flight; no queued tick */}
                    !visible -> queuedHidden = true // queue exactly one while hidden
                    else -> _ticks.tryEmit(Unit)
                }
            }
        }
    }
}
```

Notes:
- This preserves the “immediate tick on first visible” behavior (optional per product needs).
- If you prefer to always wait `intervalMs` even on the very first start, remove the initial `if (visible) _ticks.tryEmit(Unit)` in `start()`.

---

## Acceptance Criteria

- If a request completes after the interval has elapsed, the next request does not start immediately; it starts only after a new full `refreshSeconds` countdown (when visible).
- While hidden and not in-flight, the wall clock continues and queues exactly one request to fire on visibility.
- If hidden during a request, on success the ad is prefetched; after completion, the timer restarts (no immediate extra request due to elapsed time while loading).
- Single-flight preserved: never more than one active request at a time.

---

## Status of the “Transient Failure” Issue

The second issue has already been fixed in code. Now, transient failures emit an error and continue cadence (no teardown).

Excerpt:

```kotlin
when (val outcome = loader.loadOnce()) {
    // ...
    BannerLoadOutcome.TransientFailure ->
        listener?.onAdLoadFailed(
            CloudXAdError(
                "Temporary error",
                CLXErrorCode.SERVER_ERROR.code
            )
        )
    BannerLoadOutcome.PermanentFailure -> {
        stopPermanently("Permanent error", CLXErrorCode.CLIENT_ERROR.code)
        return@launch
    }
    // ...
}
```

---

## Next Steps

- If you’d like, I can implement the clock change above and run unit tests to ensure:
  - No immediate back-to-back request after completion.
  - Hidden queue semantics still emit exactly one on visibility.
  - Existing BannerManagerImpl test continues to pass (or adjust it if it relied on back-to-back behavior).

