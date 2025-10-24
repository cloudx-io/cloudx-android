package io.cloudx.sdk.internal.privacy

import io.cloudx.sdk.internal.ApplicationContext

/**
 * (CCPA) Provides IAB US privacy string
 */
internal interface USPrivacyProvider {

    /**
     * IAB Us privacy string from the default SharedPreferences using the key _IABUSPrivacy_String_. [Link](https://github.com/InteractiveAdvertisingBureau/USPrivacy/blob/master/CCPA/US%20Privacy%20String.md)
     *
     *
     * @return us privacy string; null if blank or doesn't exist.
     */
    suspend fun usPrivacyString(): String?
}

internal fun USPrivacyProvider(): USPrivacyProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    USPrivacyProviderImpl.create(ApplicationContext())
}