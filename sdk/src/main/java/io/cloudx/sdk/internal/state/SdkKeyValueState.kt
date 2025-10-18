package io.cloudx.sdk.internal.state

import io.cloudx.sdk.internal.config.Config

internal object SdkKeyValueState {

    var hashedUserId: String? = null
    
    /**
     * Force test mode flag. When true, all bid requests include test:1 regardless of build config.
     * Used by demo/test apps to enable test mode in release builds.
     */
    var forceTestMode: Boolean = false

    val userKeyValues: MutableMap<String, String> = mutableMapOf()
    val appKeyValues: MutableMap<String, String> = mutableMapOf()

    private var configPaths: Config.KeyValuePaths? = null

    fun clear() {
        hashedUserId = null
        userKeyValues.clear()
        appKeyValues.clear()
    }

    fun setKeyValuePaths(configPaths: Config.KeyValuePaths?) {
        this.configPaths = configPaths
    }

    fun getKeyValuePaths(): Config.KeyValuePaths? {
        return configPaths
    }
}
