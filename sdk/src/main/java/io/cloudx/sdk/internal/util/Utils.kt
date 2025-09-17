package io.cloudx.sdk.internal.util

import android.content.Context

internal fun Context.dpToPx(dp: Int): Int =
    dpToPx(dp, resources?.displayMetrics?.density ?: 1f)

internal fun Context.pxToDp(px: Int): Int =
    pxToDp(px, resources?.displayMetrics?.density ?: 1f)

internal fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()

internal fun pxToDp(px: Int, density: Float): Int = (px / density).toInt()

fun utcNowEpochMillis() = System.currentTimeMillis()

fun normalizeAndHash(dataToHash: String, algo: String = "md5"): String {
    val normalized = dataToHash.trim().lowercase()
    val digest = when (algo) {
        "md5" -> "MD5"
        "sha1" -> "SHA-1"
        "sha256" -> "SHA-256"
        else -> throw IllegalArgumentException("Unsupported hash algorithm: $algo")
    }
    return java.math.BigInteger(
        1, java.security.MessageDigest.getInstance(digest)
            .digest(normalized.toByteArray())
    ).toString(16).padStart(
        when (digest) {
            "MD5" -> 32
            "SHA-1" -> 40
            else -> 64 // SHA-256
        }, '0'
    )
}
