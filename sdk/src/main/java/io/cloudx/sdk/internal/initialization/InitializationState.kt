package io.cloudx.sdk.internal.initialization

internal sealed class InitializationState {
    data object Uninitialized : InitializationState()
    data object Initializing : InitializationState()
    data class Initialized(val initializationService: InitializationService) :
        InitializationState()
}