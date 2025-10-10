package io.cloudx.sdk.internal.privacy

import android.content.Context
import android.content.SharedPreferences
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.util.createIabSharedPreferences
import kotlin.coroutines.cancellation.CancellationException

internal class USPrivacyProviderImpl(
    private val sharedPrefs: SharedPreferences
) : USPrivacyProvider {

    override suspend fun usPrivacyString(): String? {
        val usPrivacy = try {
            sharedPrefs.getString(IABUSPrivacy_String, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // In case value wasn't string, handle exception gracefully.
            CXLogger.e("USPrivacyProvider", "Failed to read US Privacy string", e)
            null
        }

        return if (usPrivacy.isNullOrBlank()) {
            null
        } else {
            usPrivacy
        }
    }

    companion object {
        /**
         * Creates a USPrivacyProviderImpl using the default shared preferences.
         * This is the IAB US Privacy standard location for privacy strings.
         */
        fun create(context: Context): USPrivacyProviderImpl {
            return USPrivacyProviderImpl(context.createIabSharedPreferences())
        }
    }
}

internal const val IABUSPrivacy_String = "IABUSPrivacy_String"