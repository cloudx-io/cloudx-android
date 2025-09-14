package io.cloudx.sdk

sealed class CloudXInitializationServer(val url: String) {
    object Production : CloudXInitializationServer("https://pro.cloudx.io/sdk")
    object Development : CloudXInitializationServer("https://pro-dev.cloudx.io/sdk")
    class Custom(url: String) : CloudXInitializationServer(url)

    companion object {
        @JvmStatic
        fun production(): CloudXInitializationServer = Production

        @JvmStatic
        fun development(): CloudXInitializationServer = Development

        @JvmStatic
        fun custom(url: String): CloudXInitializationServer = Custom(url)
    }
}