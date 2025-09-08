package io.cloudx.sdk.internal.common.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import io.cloudx.sdk.internal.common.CloudXMainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal interface AppLifecycleService {

    val isResumed: StateFlow<Boolean>
    suspend fun awaitAppResume()
}

internal fun AppLifecycleService(): AppLifecycleService = LazySingleInstance

private val LazySingleInstance by lazy {
    AndroidAppLifecycleService()
}

private class AndroidAppLifecycleService : AppLifecycleService {

    private val _isResumed = MutableStateFlow(false)
    override val isResumed: StateFlow<Boolean> get() = _isResumed

    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _isResumed.value = true
            Lifecycle.Event.ON_STOP  -> _isResumed.value = false
            else -> Unit
        }
    }.also {
        CloudXMainScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(it)
        }
    }

    override suspend fun awaitAppResume() {
        isResumed.first { it }
    }
}