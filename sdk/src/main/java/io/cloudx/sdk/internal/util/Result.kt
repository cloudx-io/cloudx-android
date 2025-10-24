package io.cloudx.sdk.internal.util

/**
 * Helper class, purposed mainly as an explicit success/failure function result.
 */
sealed class Result<R, E> {
    data class Success<R, E>(val value: R) : Result<R, E>()
    data class Failure<R, E>(val value: E) : Result<R, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /**
     * Executes the appropriate lambda based on the result type.
     * @param onSuccess Called with the success value if this is a Success
     * @param onFailure Called with the failure value if this is a Failure
     */
    inline fun fold(onSuccess: (R) -> Unit, onFailure: (E) -> Unit) {
        when (this) {
            is Success -> onSuccess(this.value)
            is Failure -> onFailure(this.value)
        }
    }

    /**
     * Executes the appropriate lambda and returns a transformed result.
     * @param onSuccess Called with the success value, returns new success value
     * @param onFailure Called with the failure value, returns new failure value
     */
    inline fun <T, F> fold(onSuccess: (R) -> T, onFailure: (E) -> F): Result<T, F> {
        return when (this) {
            is Success -> Success(onSuccess(this.value))
            is Failure -> Failure(onFailure(this.value))
        }
    }

    /**
     * Executes the lambda only if this is a Success.
     * @param action Lambda to execute with the success value
     */
    inline fun onSuccess(action: (R) -> Unit): Result<R, E> {
        if (this is Success) {
            action(this.value)
        }
        return this
    }

    /**
     * Executes the lambda only if this is a Failure.
     * @param action Lambda to execute with the failure value
     */
    inline fun onFailure(action: (E) -> Unit): Result<R, E> {
        if (this is Failure) {
            action(this.value)
        }
        return this
    }

    /**
     * Maps the success value to a new type, leaving failures unchanged.
     * @param transform Function to transform the success value
     */
    inline fun <T> map(transform: (R) -> T): Result<T, E> {
        return when (this) {
            is Success -> Success(transform(this.value))
            is Failure -> Failure(this.value)
        }
    }

    /**
     * Maps the failure value to a new type, leaving successes unchanged.
     * @param transform Function to transform the failure value
     */
    inline fun <F> mapFailure(transform: (E) -> F): Result<R, F> {
        return when (this) {
            is Success -> Success(this.value)
            is Failure -> Failure(transform(this.value))
        }
    }

    /**
     * Flat maps the success value, allowing transformation to another Result.
     * @param transform Function that transforms success value to a new Result
     */
    inline fun <T> flatMap(transform: (R) -> Result<T, E>): Result<T, E> {
        return when (this) {
            is Success -> transform(this.value)
            is Failure -> Failure(this.value)
        }
    }
}

fun <R, E> R.toSuccess(): Result.Success<R, E> = Result.Success(this)
fun <R, E> E.toFailure(): Result.Failure<R, E> = Result.Failure(this)
