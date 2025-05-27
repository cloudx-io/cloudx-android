package io.cloudx.sdk.internal.privacy

import android.content.Context
import android.preference.PreferenceManager
import io.cloudx.sdk.internal.CloudXLogger

internal class TCFProviderImpl(context: Context) : TCFProvider {

    @Suppress("DEPRECATION")
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    override suspend fun tcString(): String? {
        val tcfConsent = try {
            sharedPrefs.getString(IABTCF_TCString, null)
        } catch (e: Exception) {
            // In case value wasn't string, handle exception gracefully.
            CloudXLogger.error(TAG, e.toString())
            null
        }

        return if (tcfConsent.isNullOrBlank()) {
            null
        } else {
            tcfConsent
        }
    }

    override suspend fun gdprApplies(): Boolean? {
        val key = IABTCF_gdprApplies

        // We need to do explicit check for the key existence because the default value can't be non-null
        if (!sharedPrefs.contains(key)) {
            return null
        }

        // Is the gdprApplies a boolean or integer - https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/issues/340
        // > Note: For mobile all booleans are written as Number (integer)
        // https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#what-does-the-gdprapplies-value-mean
        return try {
            when (sharedPrefs.getInt(key, 0)) {
                0 -> false
                1 -> true
                else -> null
            }
        } catch (e: Exception) {
            // In case value wasn't int, handle exception gracefully.
            CloudXLogger.error(TAG, e.toString())
            null
        }
    }
}

private const val TAG = "TCFProviderImpl"

internal const val IABTCF_TCString = "IABTCF_TCString"
internal const val IABTCF_gdprApplies = "IABTCF_gdprApplies"