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
 * Visibility-driven refresh clock (updated MVP):
 * - Emits an immediate tick when becoming visible and not in-flight.
 * - Pauses completely while hidden (no queuing and no accrual).
 * - Does not accrue delay while a request is in-flight (prevents stacking).
 * - After a request finishes, if still visible, waits a full interval before next tick.
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

    override fun start() {
        if (job != null) return
        require(intervalMs >= 1000) { "Refresh interval must be >= 1s" }
        if (visible && !inflight) {
            // Immediate first-visible tick, then start interval loop
            _ticks.tryEmit(Unit)
            scheduleLoop()
        }
    }

    override fun stop() {
        job?.cancel(); job = null
        inflight = false
    }

    override fun setVisible(v: Boolean) {
        val was = visible
        visible = v
        when {
            // Became visible: immediate tick if not inflight, then start loop
            visible && !was && !inflight -> {
                _ticks.tryEmit(Unit)
                scheduleLoop()
            }
            // Became hidden: pause cadence
            !visible && was -> {
                job?.cancel(); job = null
            }
        }
    }

    override fun markRequestStarted() {
        inflight = true
        // Do not accrue delay while in-flight
        job?.cancel(); job = null
    }

    override fun markRequestFinished() {
        inflight = false
        // Restart cadence from completion if visible
        if (visible) scheduleLoop()
    }

    private fun scheduleLoop() {
        job?.cancel()
        if (!visible || inflight) return
        job = scope.launch {
            while (isActive && visible && !inflight) {
                delay(intervalMs)
                if (!visible || inflight) break
                _ticks.tryEmit(Unit)
            }
        }
    }
}
