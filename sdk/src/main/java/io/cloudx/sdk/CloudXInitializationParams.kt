package io.cloudx.sdk

/**
 * Initialization params
 *
 * @property appKey - Identifier of the publisher app registered with CloudX.
 */
data class CloudXInitializationParams @JvmOverloads constructor(
    val appKey: String,
    @Deprecated(
        message = "This parameter is for CloudX internal testing only.",
        level = DeprecationLevel.WARNING
    )
    val initServer: CloudXInitializationServer = CloudXInitializationServer.Production
)