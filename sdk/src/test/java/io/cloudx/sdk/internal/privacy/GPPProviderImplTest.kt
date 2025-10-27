package io.cloudx.sdk.internal.privacy

import android.content.SharedPreferences
import android.util.Base64
import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GPPProviderImpl (Global Privacy Platform provider).
 *
 * IMPORTANT: GPP strings and Section IDs are PROVIDED BY EXTERNAL CMPs (Consent Management Platforms)
 * such as:
 * Google UMP (User Messaging Platform),
 * OneTrust,
 * Sourcepoint, etc.
 *
 * - CMPs write to SharedPreferences using IAB standard keys:
 *   - IABGPP_HDR_GppString - The full encoded GPP consent string
 *   - IABGPP_GppSID - Section ID(s) indicating which jurisdictions apply
 *
 * - The SDK READS these values but does NOT generate or validate them
 * - We trust that CMPs follow the IAB GPP specification correctly
 * - Our job: Parse what CMPs give us, handle edge cases gracefully
 *
 * Test Coverage:
 * - Section A: Reading GPP strings from SharedPreferences
 * - Section B: Parsing Section IDs (SIDs) from SharedPreferences
 * - Section H: Business logic for determining PII removal requirements
 * - Sections C-G (GPP decoding): Deferred - requires real GPP test strings from CMP
 *
 * References:
 * - IAB GPP Spec: https://github.com/InteractiveAdvertisingBureau/Global-Privacy-Platform
 * - Google UMP: https://developers.google.com/admob/android/privacy/us-iab-support
 * - GPP Encoder/Decoder: https://iabgpp.com/
 */
class GPPProviderImplTest : CXTest() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var subject: GPPProviderImpl

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            val input = firstArg<String>()
            java.util.Base64.getUrlDecoder().decode(input)
        }

        sharedPrefs = mockk(relaxed = true)
        subject = GPPProviderImpl(sharedPrefs)
    }

    // ========== Section A: gppString() - SharedPreferences Reading ==========

    @Test
    fun `gppString - returns correct GPP string when set`() = runTest {
        // Given
        val expectedGppString = "DBABMA~DBABtYA~BAAAAAAA"
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns expectedGppString

        // When
        val result = subject.gppString()

        // Then
        assertThat(result).isEqualTo(expectedGppString)
    }

    @Test
    fun `gppString - returns null when GPP not available`() = runTest {
        // Given
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns null

        // When
        val result = subject.gppString()

        // Then
        assertThat(result).isNull()
    }

    // ========== Section B: gppSid() - Section ID Parsing ==========

    @Test
    fun `gppSid - parses underscore-separated integers`() = runTest {
        // Given - underscore is the PRIMARY IAB standard format
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7_8"

        // When
        val result = subject.gppSid()

        // Then
        assertThat(result).isEqualTo(listOf(7, 8))
    }

    @Test
    fun `gppSid - parses comma-separated integers`() = runTest {
        // Given - comma is supported as fallback/alternative format
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7,8"

        // When
        val result = subject.gppSid()

        // Then
        assertThat(result).isEqualTo(listOf(7, 8))
    }

    @Test
    fun `gppSid - sorts to match GPP string section order`() = runTest {
        // Given - CMP provides reverse order (user clicked CA first)
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "8_7"

        // When
        val result = subject.gppSid()

        // Then - GPP sections are always in numerical order in the string, must sort for correct index mapping
        assertThat(result).isEqualTo(listOf(7, 8))
    }

    @Test
    fun `gppSid - returns null when not set`() = runTest {
        // Given
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns null

        // When
        val result = subject.gppSid()

        // Then
        assertThat(result).isNull()
    }

    // ========== Section H: requiresPiiRemoval() - Business Logic ==========

    @Test
    fun `requiresPiiRemoval - returns true when saleOptOut is 1`() = runTest {
        // Given
        val consent = GppConsent(saleOptOut = 1, sharingOptOut = null)

        // When
        val result = consent.requiresPiiRemoval()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `requiresPiiRemoval - returns true when sharingOptOut is 1`() = runTest {
        // Given
        val consent = GppConsent(saleOptOut = null, sharingOptOut = 1)

        // When
        val result = consent.requiresPiiRemoval()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `requiresPiiRemoval - returns false when both are not 1`() = runTest {
        // Given - user did not opt out (value = 2 means "Did Not Opt Out")
        val consent = GppConsent(saleOptOut = 2, sharingOptOut = 2)

        // When
        val result = consent.requiresPiiRemoval()

        // Then
        assertThat(result).isFalse()
    }

    // ========== Section C: US National (SID 7) Decoding ==========

    @Test
    fun `decodeGpp - US National with opt-out`() = runTest {
        // Given - CMP provided National section with saleOptOut=1
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABLA~BAAUAAAAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7"

        // When - decode for US National target
        val result = subject.decodeGpp(GppTarget.US_NATIONAL)

        // Then - decode succeeds, saleOptOut=1 decoded from bits 18-19, PII removal required
        assertThat(result).isNotNull()
        assertThat(result?.saleOptOut).isEqualTo(1)
        assertThat(result?.requiresPiiRemoval()).isTrue()
    }

    @Test
    fun `decodeGpp - US National without opt-out`() = runTest {
        // Given - CMP provided National section with saleOptOut=2 (did not opt out)
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABLA~BAAoAAAAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7"

        // When - decode for US National target
        val result = subject.decodeGpp(GppTarget.US_NATIONAL)

        // Then - returns null (filtered because no PII removal required)
        assertThat(result).isNull()
    }

    @Test
    fun `decodeGpp - US National not present`() = runTest {
        // Given - CMP only provided CA section, no National
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABBg~BAUAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "8"

        // When - attempt to decode National
        val result = subject.decodeGpp(GppTarget.US_NATIONAL)

        // Then - returns null (section not in SID list)
        assertThat(result).isNull()
    }

    // ========== Section D: US California (SID 8) Decoding ==========

    @Test
    fun `decodeGpp - US California with opt-out`() = runTest {
        // Given - CMP provided CA section with saleOptOut=1
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABBg~BAUAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "8"

        // When - decode for US California target
        val result = subject.decodeGpp(GppTarget.US_CA)

        // Then - decode succeeds, saleOptOut=1 decoded from bits 12-13, PII removal required
        assertThat(result).isNotNull()
        assertThat(result?.saleOptOut).isEqualTo(1)
        assertThat(result?.requiresPiiRemoval()).isTrue()
    }

    @Test
    fun `decodeGpp - US California without opt-out`() = runTest {
        // Given - CMP provided CA section with saleOptOut=2 (did not opt out)
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABBg~BAoAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "8"

        // When - decode for US California target
        val result = subject.decodeGpp(GppTarget.US_CA)

        // Then - returns null (filtered because no PII removal required)
        assertThat(result).isNull()
    }

    @Test
    fun `decodeGpp - US California not present`() = runTest {
        // Given - CMP only provided National section, no CA
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABLA~BAAUAAAAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7"

        // When - attempt to decode CA
        val result = subject.decodeGpp(GppTarget.US_CA)

        // Then - returns null (section not in SID list)
        assertThat(result).isNull()
    }

    // ========== Section E: decodeGpp(target=null) - Priority Logic ==========

    @Test
    fun `decodeGpp - both sections with opt-out`() = runTest {
        // Given - CMP provided both sections, both have opt-out
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABrw~BAAUAAAAAABA.QA~BAUAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7_8"

        // When - decode without target (priority logic)
        val result = subject.decodeGpp(target = null)

        // Then - returns first section with opt-out (National in this case)
        assertThat(result).isNotNull()
        assertThat(result?.requiresPiiRemoval()).isTrue()
    }

    @Test
    fun `decodeGpp - only National has opt-out`() = runTest {
        // Given - CMP provided both sections, only National opted out
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABrw~BAAUAAAAAABA.QA~BAoAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7_8"

        // When - decode without target (priority logic)
        val result = subject.decodeGpp(target = null)

        // Then - returns the section with opt-out (National)
        assertThat(result).isNotNull()
        assertThat(result?.requiresPiiRemoval()).isTrue()
    }

    @Test
    fun `decodeGpp - neither section has opt-out`() = runTest {
        // Given - CMP provided both sections, neither opted out
        every { sharedPrefs.getString(IABGPP_GppString, null) } returns "DBABrw~BAAoAAAAAABA.QA~BAoAAABA.QA"
        every { sharedPrefs.getString(IABGPP_GppSID, null) } returns "7_8"

        // When - decode without target (priority logic)
        val result = subject.decodeGpp(target = null)

        // Then - returns first available section (National)
        assertThat(result).isNotNull()
    }
}