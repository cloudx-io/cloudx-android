package io.cloudx.sdk.internal

import io.cloudx.sdk.CloudXAdError
import io.cloudx.sdk.CloudXInitializationListener
import io.cloudx.sdk.CloudXInitializationParams
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.initialization.InitializationService
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal object CXSdk {
    private const val TAG = "CXSdk"
    private val scope = MainScope()
    private var initJob: Job? = null
    private val _initState =
        MutableStateFlow<InitializationState>(InitializationState.Uninitialized)
    internal val initState: StateFlow<InitializationState> = _initState.asStateFlow()
    internal val initializationService
        get() = (initState.value as? InitializationState.Initialized)?.initializationService

    fun initialize(
        initParams: CloudXInitializationParams,
        listener: CloudXInitializationListener?
    ) {
        if (!_initState.compareAndSet(
                expect = InitializationState.Uninitialized,
                update = InitializationState.Initializing
            )
        ) {
            when (initState.value) {
                InitializationState.Uninitialized -> {
                    // Uninitialized/Failed due to a race; let caller try again
                    onInitializationFailed(listener, "Initialization not started")
                }

                InitializationState.Initializing -> {
                    onInitializationFailed(listener, "Initialization is already in progress")
                }

                is InitializationState.Initialized -> {
                    onInitialized(listener, null)
                }
            }
            return
        }

        initJob = scope.launch {
            try {
                // Initial creation of InitializationService.
                val initService = InitializationService(
                    configApi = ConfigApi(initParams.initServer)
                )
                SdkKeyValueState.hashedUserId = initParams.hashedUserId
                initService.metricsTracker?.trackMethodCall(MetricsType.Method.SdkInitMethod)

                // Initializing SDK...
                when (val result = initService.initialize(initParams.appKey)) {
                    is Result.Failure -> {
                        onInitializationFailed(listener, result.value.effectiveMessage)
                    }

                    is Result.Success -> {
                        onInitialized(listener, initService)
                    }
                }
            } catch (ce: CancellationException) {
                // Don’t swallow coroutine cancellation
                _initState.value = InitializationState.Uninitialized
                throw ce
            } catch (e: Exception) {
                onInitializationFailed(listener, e.message.orEmpty(), e)
            }
        }
    }

    fun deinitialize() {
        initJob?.cancel()
        initializationService?.deinitialize()
        _initState.value = InitializationState.Uninitialized
    }

    private fun onInitialized(
        listener: CloudXInitializationListener?,
        initializationService: InitializationService?
    ) {
        CXLogger.i(TAG, "CloudX SDK initialization succeeded")
        initializationService?.let {
            _initState.value = InitializationState.Initialized(it)
        }
        ThreadUtils.runOnMainThread {
            listener?.onInitialized()
        }
    }

    private fun onInitializationFailed(
        listener: CloudXInitializationListener?,
        message: String,
        e: Throwable? = null
    ) {
        val str = "CloudX SDK initialization failed: $message"
        CXLogger.e(TAG, str, e)
        _initState.value = InitializationState.Uninitialized
        ThreadUtils.runOnMainThread {
            listener?.onInitializationFailed(CloudXAdError(str))
        }
    }
}