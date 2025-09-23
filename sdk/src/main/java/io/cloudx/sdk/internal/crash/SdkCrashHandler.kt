package io.cloudx.sdk.internal.crash

internal class SdkCrashHandler(
    private val originalHandler: Thread.UncaughtExceptionHandler?,
    private val reportCrash: (Thread, Throwable) -> Unit
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        reportCrash(thread, throwable)
        originalHandler?.uncaughtException(thread, throwable)
    }
}