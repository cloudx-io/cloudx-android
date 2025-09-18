package io.cloudx.sdk

/**
 * Generic CloudX Error class
 */
data class CloudXError(
    val code: CloudXErrorCode,
    val message: String? = null,
    val cause: Throwable? = null
) {
    /**
     * Gets the effective error message - uses custom message if provided,
     * otherwise falls back to the error code's default description
     */
    val effectiveMessage: String
        get() = message ?: code.description
}

fun CloudXErrorCode.toCloudXError(
    message: String? = null,
    cause: Throwable? = null
) = CloudXError(this, message, cause)

fun Throwable.toCloudXError() = CloudXError(
    code = CloudXErrorCode.UNEXPECTED_ERROR,
    message = "Unexpected error: $message",
    cause = this
)
