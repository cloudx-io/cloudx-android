package io.cloudx.sdk.internal.privacy

import android.content.Context
import android.preference.PreferenceManager
import io.cloudx.sdk.internal.CloudXLogger

internal class USPrivacyProviderImpl(context: Context) : USPrivacyProvider {

    @Suppress("DEPRECATION")
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    override suspend fun usPrivacyString(): String? {
        val usPrivacy = try {
            sharedPrefs.getString(IABUSPrivacy_String, null)
        } catch (e: Exception) {
            // In case value wasn't string, handle exception gracefully.
            CloudXLogger.e(TAG, e.toString())
            null
        }

        return if (usPrivacy.isNullOrBlank()) {
            null
        } else {
            usPrivacy
        }
    }
}

private const val TAG = "USPrivacyProviderImpl"

internal const val IABUSPrivacy_String = "IABUSPrivacy_String"