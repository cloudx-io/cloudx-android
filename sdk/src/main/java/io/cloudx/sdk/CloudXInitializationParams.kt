package io.cloudx.sdk

/**
 * Initialization params
 *
 * @property appKey - Identifier of the publisher app registered with CloudX.
 * @property initServer - endpoint to fetch an initial SDK configuration from
 */
data class CloudXInitializationParams @JvmOverloads constructor(
    val appKey: String,
    val initServer: CloudXInitializationServer = CloudXInitializationServer.Production,
    val hashedUserId: String? = null
)