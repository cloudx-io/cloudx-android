package io.cloudx.sdk.internal.adapter

import io.cloudx.sdk.CloudXError

interface CloudXAdapterErrorListener {

    fun onError(error: CloudXError)
}