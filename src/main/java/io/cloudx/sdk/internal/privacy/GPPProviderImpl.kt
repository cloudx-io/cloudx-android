package io.cloudx.sdk.internal.privacy

import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CloudXLogger

internal interface GPPProvider {
    fun gppString(): String?
    fun gppSid(): List<Int>?
    fun decodeGpp(target: GppTarget? = null): GppConsent?
}

internal fun GPPProvider(): GPPProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    GPPProviderImpl(ApplicationContext())
}

private class GPPProviderImpl(context: Context) : GPPProvider {

    @Suppress("DEPRECATION")
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    override fun gppString(): String? {
        return try {
            sharedPrefs.getString(IABGPP_GppString, null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            CloudXLogger.error(TAG, "Failed to read GPP string: ${e.message}")
            null
        }
    }

    override fun gppSid(): List<Int>? {
        return try {
            val raw = sharedPrefs.getString(IABGPP_GppSID, null)?.takeIf { it.isNotBlank() }
            raw?.trim()?.split("_")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            CloudXLogger.error(TAG, "Failed to read or parse GPP SID: ${e.message}")
            null
        }
    }

    /**
     * Decodes the GPP string for CCPA consent information.
     * Returns null if no relevant GPP data is found.
     * This implementation checks for US-California (SID=8)[usca]
     * and US-National (SID=7)[usna] GPP SIDs.
     *
     * Use https://iabgpp.com/# to encode/decode GPP strings as test cases.
     */
    override fun decodeGpp(target: GppTarget?): GppConsent? {
        val gpp = gppString() ?: return null
        val sids = gppSid() ?: return null

        if (target == null) {
            val decodedList = sids.mapNotNull { sid ->
                when (sid) {
                    8 -> decodeUsCa(gpp)
                    7 -> decodeUsNational(gpp)
                    // TODO: Add more SIDs as needed
                    else -> null
                }
            }
            return decodedList.find { it.requiresPiiRemoval() } ?: decodedList.firstOrNull()
        } else {
            return when (target) {
                GppTarget.US_CA -> decodeUsCa(gpp)
                GppTarget.US_NATIONAL -> decodeUsNational(gpp)
            }?.takeIf { it.requiresPiiRemoval() }
        }
    }

    private fun decodeUsCa(gpp: String): GppConsent? {
        val parts = gpp.split("~")
        val corePayload = parts.getOrNull(1)?.substringBefore('.') ?: return null

        val bits = base64UrlToBits(corePayload)
        fun read(start: Int, len: Int) =
            if (start + len <= bits.length) bits.substring(start, start + len)
                .toIntOrNull(2) else null

        val version = read(0, 6)
        val saleOptOutNotice = read(6, 2)
        val sharingOptOutNotice = read(8, 2)
        val saleOptOut = read(12, 2)
        val sharingOptOut = read(14, 2)

        return GppConsent(
            saleOptOutNotice = saleOptOutNotice,
            sharingOptOutNotice = sharingOptOutNotice,
            saleOptOut = saleOptOut,
            sharingOptOut = sharingOptOut
        )
    }

    private fun decodeUsNational(gpp: String): GppConsent? {
        // GPP: <header>~<USNational payload>[.<GPC?>]
        val payload = gpp.split("~").getOrNull(1)?.substringBefore('.') ?: return null
        val bits = base64UrlToBits(payload)

        val saleOptOutNoticeBit = bits.getOrNull(8)?.digitToIntOrNull()    // 0/1
        val sharingOptOutBit = bits.getOrNull(15)?.digitToIntOrNull()      // 0/1
        val targetedOptOutBit = bits.getOrNull(16)?.digitToIntOrNull()     // 0/1

        // Map to USCA-like 2-bit semantics just to drive PII rules:
        val saleOptOutNotice = when (saleOptOutNoticeBit) {
            1 -> 1 // "Yes" (notice provided)
            0 -> 2 // "No" (treat as notice not provided)
            else -> null
        }
        val sharingOptOut = when (sharingOptOutBit) {
            1 -> 1 // Opted out
            0 -> 2 // Did not opt out
            else -> null
        }
        // We’ll fold targetedOptOut into "sharing" effect for decisioning:
        val sharingOptOutEffective = sharingOptOut ?: when (targetedOptOutBit) {
            1 -> 1
            0 -> 2
            else -> null
        }

        return GppConsent(
            saleOptOutNotice = saleOptOutNotice,
            sharingOptOutNotice = 1,       // assume a notice was provided if the bit model lacks it
            saleOptOut = 0,       // unknown/N/A in this model
            sharingOptOut = sharingOptOutEffective,
        )
    }

    private fun base64UrlToBits(encoded: String): String {
        val b64 = encoded.replace('-', '+').replace('_', '/')
        val pad = "=".repeat((4 - (b64.length % 4)) % 4)
        val decoded = Base64.decode(b64 + pad, Base64.URL_SAFE or Base64.NO_WRAP)
        return decoded.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
        }
    }

    companion object {
        private const val TAG = "GPPProviderImpl"
    }
}

internal data class GppConsent(
    // USCA (SID=8) core:
    val saleOptOutNotice: Int?,        // 0=N/A, 1=Yes, 2=No
    val sharingOptOutNotice: Int?,     // 0=N/A, 1=Yes, 2=No
    val saleOptOut: Int?,              // 0=N/A, 1=OptOut, 2=DidNotOptOut
    val sharingOptOut: Int?,           // 0=N/A, 1=OptOut, 2=DidNotOptOut
) {
    fun requiresPiiRemoval(): Boolean {
        // Stage-1 rules you gave:
        // - If notice not provided -> obfuscate PII
        // - If SharingOptOut == 1 OR SaleOptOut == 1 -> obfuscate PII
        // - (Optional) If GPC == true -> obfuscate PII
        return (saleOptOut == 1) || (sharingOptOut == 1) || (saleOptOutNotice == 2) || (sharingOptOutNotice == 2)
    }
}

/**
 * This enum class defines the GPP targets for decoding.
 * It includes US California (SID=8) and US National (SID=7).
 * These targets are used for initial SDK release and may be deprecated in the future
 */
internal enum class GppTarget {
    US_CA,       // US California (SID=8)
    US_NATIONAL, // US National (SID=7)
}
