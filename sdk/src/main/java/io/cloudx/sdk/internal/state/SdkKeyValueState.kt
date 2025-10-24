package io.cloudx.sdk.internal.state

import io.cloudx.sdk.internal.config.Config

internal object SdkKeyValueState {

    var hashedUserId: String? = null

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
