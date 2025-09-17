package io.cloudx.sdk.internal.common

import io.cloudx.sdk.Destroyable
import io.cloudx.sdk.internal.util.utcNowEpochMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Not the most precise timer but good enough for my purposes (banner).
// I don't want to use a native Java Timer for now: not sure about memory usage and things like that.
internal class SuspendableTimer : Destroyable {

    // TODO. Consider reusing scopes from outside via lambda creator or something
    //  in order to prevent multiple coroutine scopes. I'm not sure if it's even important for now.
    private val scope = CoroutineScope(Dispatchers.Main)
    private val isTimeout = MutableStateFlow(false)
    private var timeoutJob: Job? = null
    private var remainingTimeMillis: Long? = null
    private var startMillis = 0L

    // Public API
    suspend fun awaitTimeout() {
        isTimeout.first { it }
    }

    // TODO. Rename.
    fun reset(millis: Long, autoStart: Boolean = true) {
        timeoutJob?.cancel()

        isTimeout.value = false
        remainingTimeMillis = millis

        if (autoStart) resume(millis)
    }

    // TODO. Refactor if.
    fun resume() {
        val remainingTimeMillis = this.remainingTimeMillis
        if (remainingTimeMillis == null || isTimeout.value || timeoutJob?.isActive == true) {
            return
        }

        resume(remainingTimeMillis)
    }

    // TODO. Refactor if.
    fun pause() {
        val remainingTimeMillis = this.remainingTimeMillis
        if (remainingTimeMillis == null || isTimeout.value || timeoutJob?.isActive != true) {
            return
        }

        timeoutJob?.cancel()

        this.remainingTimeMillis = (remainingTimeMillis - elapsedTime()).coerceAtLeast(0)
    }

    override fun destroy() {
        scope.cancel()
    }

    // Private implementation
    private fun resume(millis: Long) {
        timeoutJob = scope.launch {
            startElapsedTimeCount()

            delay(millis)

            isTimeout.value = true
            remainingTimeMillis = null
        }
    }

    private fun startElapsedTimeCount() {
        startMillis = utcNowEpochMillis()
    }

    private fun elapsedTime(): Long {
        return (utcNowEpochMillis() - startMillis).coerceAtLeast(0)
    }
}