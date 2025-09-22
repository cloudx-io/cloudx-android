package io.cloudx.sdk

interface CloudXDestroyable {

    /**
     * Release any unmanaged resources. Mostly used for ad instances, when Activity is destroyed, or [CloudXDestroyable] instance is not required anymore.
     */
    fun destroy()
}