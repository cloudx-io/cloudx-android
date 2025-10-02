package io.cloudx.sdk.internal.tracker

import android.util.Base64
import java.nio.ByteBuffer

object XorEncryption {

    private val STATIC_SECRET = String(charArrayOf('c','l','o','u','d','x'))

    fun generateXorSecret(accountId: String): ByteArray {
        val reversedHash = accountId.reversed().xorHashCode()
        return intToByteArray(reversedHash)
    }

    fun generateCampaignIdBase64(accountId: String): String {
        val hashA = STATIC_SECRET.xorHashCode()
        val hashB = accountId.reversed().xorHashCode()
        val xor = hashA xor hashB
        return Base64.encodeToString(intToByteArray(xor), Base64.NO_WRAP)
    }

    fun encrypt(payload: String, secret: ByteArray): String {
        val inputBytes = payload.toByteArray(Charsets.UTF_8)
        val encryptedBytes = xorWithSecretIntChunks(inputBytes, secret)
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    private fun xorWithSecretIntChunks(input: ByteArray, secret: ByteArray): ByteArray {
        val secretInt = ByteBuffer.wrap(secret).int
        val paddedLen = ((input.size + 3) / 4) * 4
        val paddedInput = ByteArray(paddedLen)
        System.arraycopy(input, 0, paddedInput, 0, input.size)
        val out = ByteArray(paddedLen)
        for (i in paddedInput.indices step 4) {
            val chunkInt = ByteBuffer.wrap(paddedInput, i, 4).int
            val xored = chunkInt xor secretInt
            val bytes = ByteBuffer.allocate(4).putInt(xored).array()
            System.arraycopy(bytes, 0, out, i, 4)
        }
        return out.sliceArray(0 until input.size)
    }

    private fun intToByteArray(value: Int): ByteArray = ByteArray(4) { i -> (value shr (8 * (3 - i)) and 0xFF).toByte() }

    private fun String.xorHashCode(): Int {
        val input = this.toByteArray(Charsets.UTF_8)
        val paddedLen = ((input.size + 3) / 4) * 4
        val padded = ByteArray(paddedLen)
        System.arraycopy(input, 0, padded, 0, input.size)
        var out = ByteBuffer.wrap(padded, 0, 4).int
        for (i in 4 until padded.size step 4) {
            val chunk = ByteBuffer.wrap(padded, i, 4).int
            out = out xor chunk
        }
        return out
    }
}
