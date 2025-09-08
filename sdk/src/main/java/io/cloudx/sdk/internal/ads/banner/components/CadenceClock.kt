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
