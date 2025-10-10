package io.cloudx.sdk.internal.privacy

import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import kotlinx.coroutines.flow.MutableStateFlow

// Lazy singleton
private val LazySingleInstance by lazy {
    PrivacyService(
        tcfProvider = TCFProvider(),
        usPrivacyProvider = USPrivacyProvider(),
        gppProvider = GPPProvider()
    )
}

// Factory function
internal fun PrivacyService(): PrivacyService = LazySingleInstance

/**
 * Facade Privacy API: contains publisher defined flag based privacy data, IAB based APIs (us string, tcf string) as well.
 */
internal class PrivacyService(
    tcfProvider: TCFProvider,
    usPrivacyProvider: USPrivacyProvider,
    val gppProvider: GPPProvider
) : TCFProvider by tcfProvider, USPrivacyProvider by usPrivacyProvider,
    GPPProvider by gppProvider {

    /**
     * Holds privacy data set explicitly by publishers (COPPA, GDPR consent, Do Not Sell values etc),
     * which are supposed to be used when there's no better alternative available
     * such as TCF TC string (GDPR) or US Privacy String (CCPA)
     */
    val cloudXPrivacy: MutableStateFlow<CloudXPrivacy> = MutableStateFlow(CloudXPrivacy())

    fun shouldClearPersonalData(): Boolean {
        val isUSUser = GeoInfoHolder.isUSUser()
        if (!isUSUser) {
            return false // Non US users do not require personal data clearing
        }

        // US user
        val isCoppa = isCoppaEnabled()
        if (isCoppa) {
            return true // COPPA users always require personal data clearing within the US
        }

        val isCaliforniaUser = GeoInfoHolder.isCaliforniaUser()
        val gppConsent = if (isCaliforniaUser) {
            decodeGpp(GppTarget.US_CA)
        } else {
            decodeGpp(GppTarget.US_NATIONAL)
        }
        val clear = gppConsent?.requiresPiiRemoval() == true
        return clear
    }

    fun isCoppaEnabled(): Boolean {
        return cloudXPrivacy.value.isAgeRestrictedUser == true
    }
}
