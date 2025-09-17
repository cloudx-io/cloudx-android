package io.cloudx.sdk

/**
 * Interface for listening to CLoudX initialization status updates.
 */
interface CloudXInitializationListener {
    fun onInitialized()
    fun onInitializationFailed(cloudXError: CloudXError)
}