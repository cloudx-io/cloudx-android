import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal interface CadenceClock {
    val ticks: SharedFlow<Unit>
    fun start()
    fun stop()
    fun setVisible(visible: Boolean)
    fun markRequestStarted()
    fun markRequestFinished()
}

/**
 * Free-running wall clock.
 * - If a tick happens while a request is in-flight -> queue EXACTLY ONE.
 * - If a tick happens while hidden -> queue EXACTLY ONE.
 * - When finishing in-flight:
 *      - if visible and queuedInFlight -> emit one and clear.
 *      - if hidden -> keep hidden queue until visible.
 * - When becoming visible:
 *      - if not in-flight and queuedHidden -> emit one and clear.
 */
internal class VisibilityAwareRefreshClock(
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
        // Optional: immediate tick if already visible; otherwise, queue one for when it becomes visible
        if (visible) _ticks.tryEmit(Unit) else queuedHidden = true
        scheduleLoop()
    }

    override fun stop() {
        job?.cancel()
        job = null
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
        // If we became visible while in-flight and a hidden tick is queued,
        // emit it immediately now (earliest feasible time) and skip restarting delay.
        if (visible && queuedHidden) {
            queuedHidden = false
            _ticks.tryEmit(Unit)
            return
        }
        // Otherwise, restart the cadence: next tick after a full interval
        scheduleLoop()
    }

    private fun scheduleLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                when {
                    // Prioritize hidden queue regardless of in-flight status, so wall-clock while hidden is preserved
                    !visible -> queuedHidden = true // queue exactly one while hidden
                    inflight -> {
                        // Ignore elapsed time while a request is in-flight; no immediate emit, hidden already handled above
                    }
                    else -> _ticks.tryEmit(Unit)
                }
            }
        }
    }
}
