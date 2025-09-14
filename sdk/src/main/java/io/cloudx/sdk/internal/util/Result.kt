package io.cloudx.sdk.internal.util

/**
 * @suppress
 * Helper class, purposed mainly as an explicit success/failure function result.
 */
sealed class Result<R, E> {
    data class Success<R, E>(val value: R) : Result<R, E>()
    data class Failure<R, E>(val value: E) : Result<R, E>()
}