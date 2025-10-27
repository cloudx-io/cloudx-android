package io.cloudx.sdk

/**
 * CloudX privacy object, passed into [CloudX.setPrivacy] API
 *
 * @property isUserConsent GDPR flag; if _null_ - flag not set
 * @property isAgeRestrictedUser COPPA flag; if _null_ - flag not set
 */
data class CloudXPrivacy(
    @JvmField val isUserConsent: Boolean? = null,
    @JvmField val isAgeRestrictedUser: Boolean? = null
)