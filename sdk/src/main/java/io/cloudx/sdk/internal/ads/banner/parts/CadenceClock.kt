package io.cloudx.sdk.internal.ads.banner.parts

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
    fun markRequestStarted()
    fun markRequestFinished()
}

/**
 * Policy: free-running wall-clock. While a request is in-flight,
 * we queue EXACTLY ONE tick. When the request finishes we emit it.
 */
internal class FreeRunningOneQueuedTickClock(
    private val intervalMs: Long,
    private val scope: CoroutineScope
) : CadenceClock {

    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val ticks: SharedFlow<Unit> = _ticks.asSharedFlow()

    private var job: Job? = null
    private var inflight: Boolean = false
    private var pending: Boolean = false

    override fun start() {
        if (job != null) return
        require(intervalMs >= 1000) { "Refresh interval must be >= 1s" }
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (inflight) {
                    pending = true
                } else {
                    _ticks.tryEmit(Unit)
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        pending = false
        inflight = false
    }

    override fun markRequestStarted() {
        inflight = true
    }

    override fun markRequestFinished() {
        inflight = false
        if (pending) {
            pending = false
            _ticks.tryEmit(Unit)
        }
    }
}
