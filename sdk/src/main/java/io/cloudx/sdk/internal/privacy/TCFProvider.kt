package io.cloudx.sdk.internal.privacy

import io.cloudx.sdk.internal.ApplicationContext

/**
 * (GDPR) IAB TCF data provider (tc string, gdpr applies flag etc)
 */
internal interface TCFProvider {

    /**
     * IAB Transparency and Consent Framework (TCF) string from the default SharedPreferences using the key _IABTCF_TCString_
     *
     * @return The TCF string if it exists and is not blank, otherwise `null` is returned
     */
    suspend fun tcString(): String?

    /**
     * IAB Transparency and Consent Framework _gdprApplies_ value from the default SharedPreferences using the key _IABTCF_gdprApplies_.
     *
     * See the [link](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#what-does-the-gdprapplies-value-mean) on what does `gdprApplies` value mean
     * @return
     * - _true_ - GDPR Applies
     * - _false_ - GDPR Does not apply
     * - _null_ - unknown whether GDPR applies
     */
    suspend fun gdprApplies(): Boolean?
}

internal fun TCFProvider(): TCFProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    TCFProviderImpl.create(ApplicationContext())
}