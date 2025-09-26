package io.cloudx.sdk.fake

import io.cloudx.sdk.internal.config.ConfigRequest
import io.cloudx.sdk.internal.config.ConfigRequestProvider

internal class FakeConfigRequestProviderWithArbitraryValues : ConfigRequestProvider {
    override suspend fun invoke(): ConfigRequest {
        return ConfigRequest(
            bundle = "io.cloudx.mock.bundle",
            os = "Android",
            osVersion = "14",
            deviceManufacturer = "Samsung",
            deviceModel = "a20",
            sdkVersion = "mockSdkVersion",
            gaid = "mock-gaid-string",
            dnt = true
        )
    }
}