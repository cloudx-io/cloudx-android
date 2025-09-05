package io.cloudx.sdk

/**
 * Interface for listening to CLoudX initialization status updates.
 */
fun interface CloudXInitializationListener {

    /**
     * Called when there is an update to the CloudX initialization status.
     * @param status the updated initialization status.
     */
    fun onCloudXInitializationStatus(status: CloudXInitializationStatus)
}

class CloudXInitializationStatus(val initialized: Boolean, val description: String, val errorCode: Int? = null)