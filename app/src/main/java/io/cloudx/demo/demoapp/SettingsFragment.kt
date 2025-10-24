package io.cloudx.demo.demoapp

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        gppTestLogic()
        updatePlacementsFromPreferences()
        updateUserId()
    }

    fun updateUserId() {
        val context = preferenceManager.context

        val userIdKey = getString(R.string.pref_user_id)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val userIdPref = findPreference<EditTextPreference>(userIdKey)
        val userIdRaw = prefs.getString(userIdKey, null)

        if (!userIdRaw.isNullOrBlank()) {
            userIdPref?.text = userIdRaw
            userIdPref?.isVisible = true
        } else {
            userIdPref?.text = null
            userIdPref?.isVisible = false
        }
    }

    private fun gppTestLogic() {
        val allowAllPref = findPreference<Preference>("pref_gpp_allow_all")
        val disallowAllPref = findPreference<Preference>("pref_gpp_disallow_all")

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val gppStringPref = findPreference<EditTextPreference>("pref_iab_gpp_string")
        val gppSidPref = findPreference<EditTextPreference>("pref_iab_gpp_sid")

        allowAllPref?.setOnPreferenceClickListener {
            // 🔓 User allows personalized ads, sharing, and sale of personal info
            // ➡️ GPP-compliant bid requests can include personal data (IFA, hashed user id, etc.)
            val gppString = "DBABBg~BUoAAAJA.QA"
            val sid = "8"

            // Update real SDK keys
            prefs.edit().putString("IABGPP_HDR_GppString", gppString)
                .putString("IABGPP_GppSID", sid).apply()

            // Update UI-bound prefs
            gppStringPref?.text = gppString
            gppSidPref?.text = sid

            Toast.makeText(requireContext(), "GPP set to Allow All", Toast.LENGTH_SHORT).show()
            true
        }

        disallowAllPref?.setOnPreferenceClickListener {
            // 🔒 User does NOT allow targeting, sharing, or sale of personal info
            // ➡️ SDK must remove IFA, hashed user id, geo, and any PII from bid requests and tracking
            val gppString = "DBABBg~BUVVVVVA.QA"
            val sid = "8"

            prefs.edit().putString("IABGPP_HDR_GppString", gppString)
                .putString("IABGPP_GppSID", sid).apply()

            gppStringPref?.text = gppString
            gppSidPref?.text = sid

            Toast.makeText(requireContext(), "GPP set to Disallow All", Toast.LENGTH_SHORT).show()
            true
        }
    }


    fun updatePlacementsFromPreferences() {
        val context = preferenceManager.context
        val group = findPreference<PreferenceCategory>("pref_placements_category") ?: return

        group.removeAll()

        var globalOrder = 0

        fun addPlacementGroup(title: String, key: String, defaultSet: Set<String>) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val placements = context.safeGetStringSet(key, defaultSet).toList()

            // Keep track of all dynamic keys
            val dynamicKeys = mutableListOf<String>()

            placements.forEachIndexed { index, placement ->
                val dynamicKey = "${key}_$index"
                dynamicKeys.add(dynamicKey)

                val editPref = EditTextPreference(context).apply {
                    this.key = dynamicKey
                    this.title = "$title ${index + 1}"
                    this.text = placement
                    this.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                    this.order = globalOrder++

                    setOnPreferenceChangeListener { preference, newValue ->
                        // Read all other values and update the master set
                        val updatedPlacements = dynamicKeys.mapNotNull { dk ->
                            prefs.getString(dk, null)
                                // If we're changing this one right now, use the new value
                                .takeIf { dk != dynamicKey } ?: newValue?.toString()
                        }.filterNot { it.isBlank() }.toSet()

                        prefs.edit { putStringSet(key, updatedPlacements) }

                        true
                    }
                }

                group.addPreference(editPref)
            }
        }

        addPlacementGroup(
            "Banner Placement",
            getString(R.string.pref_banner_placement_name),
            setOf(getString(R.string.pref_banner_placement_name_dev_val))
        )
        addPlacementGroup(
            "MREC Placement",
            getString(R.string.pref_mrec_placement_name),
            setOf(getString(R.string.pref_mrec_placement_name_dev_val))
        )
        addPlacementGroup(
            "Interstitial Placement",
            getString(R.string.pref_interstitial_placement_name),
            setOf(getString(R.string.pref_interstitial_placement_name_dev_val))
        )
        addPlacementGroup(
            "Rewarded Placement",
            getString(R.string.pref_rewarded_placement_name),
            setOf(getString(R.string.pref_rewarded_placement_name_dev_val))
        )
        addPlacementGroup(
            "Native Small Placement",
            getString(R.string.pref_native_small_placement_name),
            setOf(getString(R.string.pref_native_small_placement_name_dev_val))
        )
        addPlacementGroup(
            "Native Medium Placement",
            getString(R.string.pref_native_medium_placement_name),
            setOf(getString(R.string.pref_native_medium_placement_name_dev_val))
        )
    }
}
