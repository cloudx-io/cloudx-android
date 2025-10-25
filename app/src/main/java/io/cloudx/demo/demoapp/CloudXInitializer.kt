package io.cloudx.demo.demoapp

import android.content.Context
import androidx.preference.PreferenceManager
import io.cloudx.adapter.meta.enableMetaAudienceNetworkTestMode
import io.cloudx.sdk.CloudX
import io.cloudx.sdk.CloudXError
import io.cloudx.sdk.CloudXInitializationListener
import io.cloudx.sdk.CloudXInitializationParams
import io.cloudx.sdk.CloudXInitializationServer
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CloudXInitializer {

    private val _initState = MutableStateFlow(InitializationState.NotInitialized)
    val initState = _initState.asStateFlow()

    fun initializeCloudX(
        context: Context,
        settings: Settings,
        logTag: String,
        listener: CloudXInitializationListener? = null
    ) {
        _initState.value = InitializationState.InProgress

        // Enable Meta test mode based on settings
        enableMetaAudienceNetworkTestMode(settings.metaTestModeEnabled)

        context.updateIabTcfGdprAppliesSharedPrefs()
        context.updateGppSharedPrefs()

        CloudX.setPrivacy(
            CloudXPrivacy(
                isUserConsent = settings.gdprConsent,
                isAgeRestrictedUser = settings.ageRestricted
            ).also {
                CXLogger.i(logTag, "CloudX privacy set: $it")
            }
        )

        CloudX.initialize(
            initParams = CloudXInitializationParams(
                appKey = settings.appKey,
                initServer = CloudXInitializationServer.Custom(settings.initUrl)
            ),
            listener = object : CloudXInitializationListener {
                override fun onInitialized() {
                    _initState.value = InitializationState.Initialized
                    listener?.onInitialized()
                }

                override fun onInitializationFailed(cloudXError: CloudXError) {
                    _initState.value = InitializationState.FailedToInitialize
                    listener?.onInitializationFailed(cloudXError)
                }
            }
        )
    }

    fun reset() {
        _initState.value = InitializationState.NotInitialized
    }
}

enum class InitializationState {
    NotInitialized, InProgress, FailedToInitialize, Initialized
}

// Can't do it directly, since that would require to write a custom preference control which saves int to shared prefs.
// So we do mapping from string -> int basically, since IABTCF_gdprApplies must hold Integer.
// > Note: For mobile all booleans are written as Number (integer)
// https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20CMP%20API%20v2.md#what-does-the-gdprapplies-value-mean
private fun Context.updateIabTcfGdprAppliesSharedPrefs() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val gdprApplies: Boolean? = prefs.toPrivacyFlag(getString(R.string.pref_iab_tcf_gdpr_applies))

    prefs.edit().apply {
        val iabGdprAppliesKey = "IABTCF_gdprApplies"

        when (gdprApplies) {
            true -> putInt(iabGdprAppliesKey, 1)
            false -> putInt(iabGdprAppliesKey, 0)
            null -> remove(iabGdprAppliesKey)
        }
        apply()
    }
}

private fun Context.updateGppSharedPrefs() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)

    val gppStringKey = "pref_iab_gpp_string"
    val gppSidKey = "pref_iab_gpp_sid"

    val gppString = prefs.getString(gppStringKey, null)
    val gppSid = prefs.getString(gppSidKey, null)

    prefs.edit().apply {
        if (!gppString.isNullOrBlank()) {
            putString("IABGPP_HDR_GppString", gppString)
        } else {
            remove("IABGPP_HDR_GppString")
        }

        if (!gppSid.isNullOrBlank()) {
            putString("IABGPP_GppSID", gppSid)
        } else {
            remove("IABGPP_GppSID")
        }

        apply()
    }
}