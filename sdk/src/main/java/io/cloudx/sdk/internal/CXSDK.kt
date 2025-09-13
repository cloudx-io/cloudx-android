package io.cloudx.sdk.internal

import io.cloudx.sdk.BuildConfig
import io.cloudx.sdk.CloudXInitializationListener
import io.cloudx.sdk.CloudXInitializationParams
import io.cloudx.sdk.CloudXInitializationStatus
import io.cloudx.sdk.Result
import io.cloudx.sdk.internal.config.ConfigApi
import io.cloudx.sdk.internal.imp_tracker.metrics.MetricsType
import io.cloudx.sdk.internal.initialization.InitializationService
import io.cloudx.sdk.internal.initialization.InitializationState
import io.cloudx.sdk.internal.state.SdkKeyValueState
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal object CXSDK {
    private const val TAG = "CXSDK"
    private val scope = MainScope()
    private var initJob: Job? = null
    private val _initState =
        MutableStateFlow<InitializationState>(InitializationState.Uninitialized)
    internal val initState: StateFlow<InitializationState> = _initState.asStateFlow()
    internal val initializationService
        get() = (initState.value as? InitializationState.Initialized)?.initializationService

    fun initialize(
        initParams: CloudXInitializationParams,
        listener: CloudXInitializationListener? = null
    ) {
        if (!_initState.compareAndSet(
                expect = InitializationState.Uninitialized,
                update = InitializationState.Initializing
            )
        ) {
            val status = when (initState.value) {
                InitializationState.Uninitialized -> {
                    // Uninitialized/Failed due to a race; let caller try again
                    CloudXInitializationStatus(
                        initialized = false,
                        description = "Initialization not started"
                    )
                }

                InitializationState.Initializing -> {
                    CloudXInitializationStatus(
                        initialized = false,
                        description = "Initialization is already in progress"
                    )
                }

                is InitializationState.Initialized -> {
                    CloudXInitializationStatus(
                        initialized = true,
                        description = "Already initialized"
                    )
                }
            }

            ThreadUtils.runOnMainThread {
                listener?.onCloudXInitializationStatus(status)
            }
            return
        }

        initJob = scope.launch {
            val initStatus = try {
                // Initial creation of InitializationService.
                val initService = InitializationService(
                    configApi = ConfigApi(initParams.initEndpointUrl)
                )
                SdkKeyValueState.hashedUserId = initParams.hashedUserId
                initService.metricsTracker?.trackMethodCall(MetricsType.Method.SdkInitMethod)

                // Initializing SDK...
                when (val result = initService.initialize(initParams.appKey)) {
                    is Result.Failure -> {
                        CloudXLogger.e(
                            TAG,
                            "SDK initialization failed: ${result.value.effectiveMessage}",
                            result.value.cause
                        )
                        _initState.value = InitializationState.Uninitialized
                        CloudXInitializationStatus(
                            initialized = false,
                            result.value.effectiveMessage,
                            result.value.code.code
                        )
                    }

                    is Result.Success -> {
                        CloudXLogger.i(TAG, "SDK initialization succeeded")
                        _initState.value = InitializationState.Initialized(initService)
                        CloudXInitializationStatus(
                            initialized = true,
                            "CloudX SDK is initialized v${BuildConfig.SDK_VERSION_NAME}"
                        )
                    }
                }
            } catch (ce: CancellationException) {
                // Don’t swallow coroutine cancellation
                _initState.value = InitializationState.Uninitialized
                throw ce
            } catch (e: Exception) {
                CloudXLogger.e(TAG, "SDK initialization failed with exception", e)
                _initState.value = InitializationState.Uninitialized
                CloudXInitializationStatus(false, "CloudX SDK failed to initialize")
            }
            CloudXLogger.i(TAG, "SDK initialization completed successfully")
            ThreadUtils.runOnMainThread {
                listener?.onCloudXInitializationStatus(initStatus)
            }
        }
    }

    fun deinitialize( ) {
        initJob?.cancel()
        initializationService?.deinitialize()
        _initState.value = InitializationState.Uninitialized
    }
}