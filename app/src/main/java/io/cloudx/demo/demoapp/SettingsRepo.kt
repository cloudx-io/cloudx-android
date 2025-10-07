package io.cloudx.demo.demoapp

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

fun Context.settings(): Settings {

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    return Settings(
        appKey = prefs.getString(getString(R.string.pref_app_key), getString(R.string.pref_app_key_def_val))!!,
        initUrl = prefs.getString(getString(R.string.pref_init_url), getString(R.string.pref_init_url_def_val))!!,
        userEmail = prefs.getString(getString(R.string.pref_user_email), getString(R.string.pref_email_def_val))!!,

        bannerPlacementNames = safeGetStringSet(
            getString(R.string.pref_banner_placement_name),
            setOf(getString(R.string.pref_banner_placement_name_dev_val))
        ).toCollection(ArrayList()),

        mrecPlacementNames = safeGetStringSet(
            getString(R.string.pref_mrec_placement_name),
            setOf(getString(R.string.pref_mrec_placement_name_dev_val))
        ).toCollection(ArrayList()),

        interstitialPlacementNames = safeGetStringSet(
            getString(R.string.pref_interstitial_placement_name),
            setOf(getString(R.string.pref_interstitial_placement_name_dev_val))
        ).toCollection(ArrayList()),

        rewardedPlacementNames = safeGetStringSet(
            getString(R.string.pref_rewarded_placement_name),
            setOf(getString(R.string.pref_rewarded_placement_name_dev_val))
        ).toCollection(ArrayList()),

        nativeSmallPlacementNames = safeGetStringSet(
            getString(R.string.pref_native_small_placement_name),
            setOf(getString(R.string.pref_native_small_placement_name_dev_val))
        ).toCollection(ArrayList()),

        nativeMediumPlacementNames = safeGetStringSet(
            getString(R.string.pref_native_medium_placement_name),
            setOf(getString(R.string.pref_native_medium_placement_name_dev_val))
        ).toCollection(ArrayList()),

        gdprConsent = prefs.toPrivacyFlag(getString(R.string.pref_gdpr_consent)),
        ageRestricted = prefs.toPrivacyFlag(getString(R.string.pref_age_restricted)),
        mockUserTargetingEnabled = prefs.getBoolean(
            getString(R.string.pref_mock_user_targeting_enabled), resources.getBoolean(R.bool.pref_mock_user_targeting_enabled_def_val)
        ),
        metaTestModeEnabled = prefs.getBoolean(
            getString(R.string.pref_meta_test_mode_enabled), resources.getBoolean(R.bool.pref_meta_test_mode_enabled_def_val)
        )
    )
}

@Suppress("UNCHECKED_CAST")
fun Context.safeGetStringSet(key: String, default: Set<String>): Set<String> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    return try {
        val value = prefs.all[key]
        when (value) {
            is Set<*> -> value as? Set<String> ?: default
            is String -> setOf(value) // backward compatibility with EditTextPreference
            else -> default
        }
    } catch (e: Exception) {
        default
    }
}

internal fun SharedPreferences.toPrivacyFlag(key: String): Boolean? =
    getString(key, null)?.toPrivacyFlag()

internal fun String.toPrivacyFlag(): Boolean? = when (this) {
    "0" -> false
    "1" -> true
    else -> null
}

data class Settings(
    val appKey: String,
    val initUrl: String,
    val userEmail: String,

    val bannerPlacementNames: ArrayList<String>,
    val mrecPlacementNames: ArrayList<String>,
    val interstitialPlacementNames: ArrayList<String>,
    val rewardedPlacementNames: ArrayList<String>,
    val nativeSmallPlacementNames: ArrayList<String>,
    val nativeMediumPlacementNames: ArrayList<String>,

    val gdprConsent: Boolean?,
    val ageRestricted: Boolean?,
    val mockUserTargetingEnabled: Boolean,
    val metaTestModeEnabled: Boolean
)