package io.cloudx.sdk

/**
 * Initialization params
 *
 * @property appKey - Identifier of the publisher app registered with CloudX.
 * @property initEndpointUrl - endpoint to fetch an initial SDK configuration from
 */
class CloudXInitializationParams(
    val appKey: String,
    val initEndpointUrl: String,
    val hashedUserId: String? = null
)