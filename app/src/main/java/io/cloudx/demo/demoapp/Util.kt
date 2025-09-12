package io.cloudx.demo.demoapp

fun normalizeAndHash(email: String, algo: String): String {
    val normalized = email.trim().lowercase()
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