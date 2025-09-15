package io.cloudx.sdk.internal.util

/**
 * Helper class, purposed mainly as an explicit success/failure function result.
 */
sealed class Result<R, E> {
    data class Success<R, E>(val value: R) : Result<R, E>()
    data class Failure<R, E>(val value: E) : Result<R, E>()

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure

    fun successOrNull(): R? =
        when (this) {
            is Failure -> null
            is Success -> this.value
        }
}
