package io.cloudx.sdk.internal.tracker

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for XorEncryption utility.
 *
 * NOTE: We mock android.util.Base64 with java.util.Base64 to avoid using Robolectric.
 * This is safe because:
 * - android.util.Base64.NO_WRAP = no line breaks, standard padding
 * - java.util.Base64.getEncoder() = same behavior (RFC 4648 compliant)
 * Both produce identical Base64 output for the same input bytes.
 */
class XorEncryptionTest : CXTest() {

    @Before
    fun setUp() {
        // Mock android.util.Base64 with java.util.Base64 to avoid Robolectric
        // NO_WRAP flag means: no line breaks, with padding (same as Java's default encoder)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } answers {
            val bytes = firstArg<ByteArray>()
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    @Test
    fun `generateXorSecret - same accountId produces same secret`() {
        // Given
        val accountId = "test-account-123"

        // When
        val secret1 = XorEncryption.generateXorSecret(accountId)
        val secret2 = XorEncryption.generateXorSecret(accountId)

        // Then
        assertThat(secret1).isEqualTo(secret2)
        assertThat(secret1.size).isEqualTo(4)
    }

    @Test
    fun `generateCampaignIdBase64 - backend reference test`() {
        // Given - confirmed working payload from backend
        val accountId = "CLDX3_dc"
        val expectedCampaignId = "PDR8BQ=="

        // When
        val result = XorEncryption.generateCampaignIdBase64(accountId)

        // Then
        assertThat(result).isEqualTo(expectedCampaignId)
    }

    @Test
    fun `encrypt - backend reference test`() {
        // Given - confirmed working payload from backend
        val accountId = "CLDX3_dc"
        val payload = "320;50;dealId_absent_from_bid;;0.000000;0;1.0.0;;CLDX3_dc;CLDX3;;;;phone;;;LId-i7QFpU90pb-rK_NAy3ABF1E4B-C513-4DCE-9C02-B6A1A07C4AE1;;0;RandomTest;;;;;;;method_sdk_init;1;"
        val expectedEncrypted = "CBIjSw4QKBReQX85X39yEkhFfQRkRmEfVn9xGV8bKEAVECNACxAjSwsbIl4LDiNLAGNfNGMTTBRYG1A8f3ggSwAbKABTT30VABsoPHJEPhkMcVUAbhkjAFkNYTtkblIJCGFRNgplJzIWYyZBCA0nNHhlPkl4ECFdeRZSQXoQJDMPYVZBABsjS2lBfRRUTUcVSFQoSwAbKEsATXYEU093L0hEeC9STnoEABEo"

        // When
        val secret = XorEncryption.generateXorSecret(accountId)
        val result = XorEncryption.encrypt(payload, secret)

        // Then
        assertThat(result).isEqualTo(expectedEncrypted)
    }

    @Test
    fun `generateCampaignIdBase64 - deterministic across multiple calls`() {
        // Given
        val accountId = "test-account-456"

        // When
        val result1 = XorEncryption.generateCampaignIdBase64(accountId)
        val result2 = XorEncryption.generateCampaignIdBase64(accountId)
        val result3 = XorEncryption.generateCampaignIdBase64(accountId)

        // Then - all calls produce identical output
        assertThat(result1).isEqualTo(result2)
        assertThat(result2).isEqualTo(result3)
    }

    @Test
    fun `generateCampaignIdBase64 - different accountIds produce different outputs`() {
        // Given
        val accountId1 = "account-A"
        val accountId2 = "account-B"
        val accountId3 = "account-C"

        // When
        val campaignId1 = XorEncryption.generateCampaignIdBase64(accountId1)
        val campaignId2 = XorEncryption.generateCampaignIdBase64(accountId2)
        val campaignId3 = XorEncryption.generateCampaignIdBase64(accountId3)

        // Then - all outputs are different
        assertThat(campaignId1).isNotEqualTo(campaignId2)
        assertThat(campaignId2).isNotEqualTo(campaignId3)
        assertThat(campaignId1).isNotEqualTo(campaignId3)
    }

    @Test
    fun `encrypt - different payloads produce different outputs`() {
        // Given
        val accountId = "test-account"
        val secret = XorEncryption.generateXorSecret(accountId)
        val payload1 = "event=impression;timestamp=1234567890"
        val payload2 = "event=click;timestamp=9876543210"
        val payload3 = "event=sdk_init;timestamp=1111111111"

        // When
        val encrypted1 = XorEncryption.encrypt(payload1, secret)
        val encrypted2 = XorEncryption.encrypt(payload2, secret)
        val encrypted3 = XorEncryption.encrypt(payload3, secret)

        // Then - all encrypted outputs are different
        assertThat(encrypted1).isNotEqualTo(encrypted2)
        assertThat(encrypted2).isNotEqualTo(encrypted3)
        assertThat(encrypted1).isNotEqualTo(encrypted3)
    }

    @Test
    fun `generateXorSecret - always returns 4 bytes`() {
        // Given - various accountIds
        val accountIds = listOf(
            "short",
            "medium-length-account-id",
            "very-long-account-id-with-many-characters-to-test-consistency",
            "special!@#$%^&*()chars"
        )

        // When/Then - all secrets are exactly 4 bytes
        accountIds.forEach { accountId ->
            val secret = XorEncryption.generateXorSecret(accountId)
            assertThat(secret.size).isEqualTo(4)
        }
    }

    @Test
    fun `generateCampaignIdBase64 - produces valid Base64 string`() {
        // Given
        val accountId = "test-account-789"

        // When
        val result = XorEncryption.generateCampaignIdBase64(accountId)

        // Then - should be valid Base64 (decodable without exception)
        val decoded = java.util.Base64.getDecoder().decode(result)
        assertThat(decoded).isNotNull()
        assertThat(result).matches("[A-Za-z0-9+/]+=*") // Base64 character set
    }

    @Test
    fun `encrypt - produces valid Base64 string`() {
        // Given
        val accountId = "test-account"
        val payload = "test-payload-data"
        val secret = XorEncryption.generateXorSecret(accountId)

        // When
        val result = XorEncryption.encrypt(payload, secret)

        // Then - should be valid Base64 (decodable without exception)
        val decoded = java.util.Base64.getDecoder().decode(result)
        assertThat(decoded).isNotNull()
        assertThat(result).matches("[A-Za-z0-9+/]+=*") // Base64 character set
    }
}
