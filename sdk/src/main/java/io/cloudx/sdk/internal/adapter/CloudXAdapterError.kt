package io.cloudx.sdk.internal.adapter

data class CloudXAdapterError(
    val code: CloudXAdapterErrorType = CloudXAdapterErrorType.General,
    val description: String = ""
)

enum class CloudXAdapterErrorType {
    Timeout,
    ShowFailed,
    General,
}

interface CloudXAdapterErrorListener {

    fun onError(error: CloudXAdapterError = CloudXAdapterError())
}