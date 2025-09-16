package io.cloudx.sdk.internal.privacy

import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.CXLogger

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
            CXLogger.e(TAG, "Failed to read GPP string: ${e.message}")
            null
        }
    }

    override fun gppSid(): List<Int>? {
        return try {
            val raw = sharedPrefs.getString(IABGPP_GppSID, null)?.takeIf { it.isNotBlank() }
            raw?.trim()
                ?.split(Regex("[_,]"))
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.distinct()
                ?.sorted()
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            CXLogger.e(TAG, "Failed to read or parse GPP SID: ${e.message}")
            null
        }
    }

    /**
     * Decodes the GPP string for CCPA consent information.
     * - when target == null: decode US-CA(8) and US-National(7), pick first requiring PII removal, else first available
     * - when target != null: return only if requiresPiiRemoval() == true
     */
    override fun decodeGpp(target: GppTarget?): GppConsent? {
        val gpp = gppString() ?: return null
        val sids = gppSid() ?: return null

        return if (target == null) {
            val decodedList = listOfNotNull(
                decodeIfPresent(gpp, sids, 8, ::decodeUsCa),
                decodeIfPresent(gpp, sids, 7, ::decodeUsNational)
            )
            decodedList.find { it.requiresPiiRemoval() } ?: decodedList.firstOrNull()
        } else {
            when (target) {
                GppTarget.US_CA -> decodeIfPresent(gpp, sids, 8, ::decodeUsCa)
                GppTarget.US_NATIONAL -> decodeIfPresent(gpp, sids, 7, ::decodeUsNational)
            }?.takeIf { it.requiresPiiRemoval() }
        }
    }

    private fun decodeUsCa(gpp: String, sids: List<Int>, sid: Int): GppConsent? {
        return try {
            val payload = selectSectionPayload(gpp, sids, sid) ?: return null
            val bits = base64UrlToBits(payload)

            fun read(start: Int, len: Int) =
                if (start + len <= bits.length) bits.substring(start, start + len)
                    .toIntOrNull(2) else null

            val saleOptOut = read(12, 2)
            val sharingOptOut = read(14, 2)

            GppConsent(
                saleOptOut = saleOptOut,
                sharingOptOut = sharingOptOut
            )
        } catch (e: Exception) {
            CXLogger.e(TAG, "US-CA decode failed: ${e.message}")
            null
        }
    }

    private fun decodeUsNational(gpp: String, sids: List<Int>, sid: Int): GppConsent? {
        return try {
            val payload = selectSectionPayload(gpp, sids, sid) ?: return null
            val bits = base64UrlToBits(payload)

            fun read2(offset: Int): Int? =
                if (offset + 2 <= bits.length) bits.substring(offset, offset + 2)
                    .toIntOrNull(2) else null

            val saleOptOut = read2(18)
            val sharingOptOut = read2(20)
            val targetedOptOut = read2(22)

            val sharingOptOutEffective = sharingOptOut ?: targetedOptOut

            GppConsent(
                saleOptOut = saleOptOut ?: 0,                // keep 0 when unknown/N/A
                sharingOptOut = sharingOptOutEffective
            )
        } catch (e: Exception) {
            CXLogger.e(TAG, "US-National decode failed: ${e.message}")
            null
        }
    }

    // ---- helpers ----

    private fun selectSectionPayload(gpp: String, sidsSorted: List<Int>, sid: Int): String? {
        val parts = gpp.split("~").filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val payloads = parts.drop(1)
        val idx = sidsSorted.indexOf(sid)
        if (idx !in payloads.indices) return null

        return payloads[idx].substringBefore('.')
    }

    private fun decodeIfPresent(
        gpp: String,
        sids: List<Int>,
        sid: Int,
        decode: (String, List<Int>, Int) -> GppConsent?
    ): GppConsent? {
        return if (sid in sids) decode(gpp, sids, sid) else null
    }

    private fun base64UrlToBits(encoded: String): String {
        val pad = "=".repeat((4 - (encoded.length % 4)) % 4)
        val decoded = Base64.decode(encoded + pad, Base64.URL_SAFE or Base64.NO_WRAP)
        return decoded.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
        }
    }

    companion object {
        private const val TAG = "GPPProviderImpl"
    }
}

internal data class GppConsent(
    val saleOptOut: Int?,              // 0=N/A, 1=OptOut, 2=DidNotOptOut
    val sharingOptOut: Int?,           // 0=N/A, 1=OptOut, 2=DidNotOptOut
) {
    fun requiresPiiRemoval(): Boolean {
        return (saleOptOut == 1) || (sharingOptOut == 1)
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
