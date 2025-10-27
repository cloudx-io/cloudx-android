package io.cloudx.sdk.internal.privacy

import com.google.common.truth.Truth.assertThat
import io.cloudx.sdk.CXTest
import io.cloudx.sdk.CloudXPrivacy
import io.cloudx.sdk.internal.geo.GeoInfoHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PrivacyService.
 *
 * Tests the business logic for determining when to clear personal data based on:
 * - User geo location (US vs non-US, California vs other states)
 * - COPPA flag (age-restricted users)
 * - GPP consent strings (opt-out signals)
 */
class PrivacyServiceTest : CXTest() {

    private lateinit var tcfProvider: TCFProvider
    private lateinit var usPrivacyProvider: USPrivacyProvider
    private lateinit var gppProvider: GPPProvider
    private lateinit var subject: PrivacyService

    @Before
    fun setUp() {
        mockkObject(GeoInfoHolder)

        tcfProvider = mockk(relaxed = true)
        usPrivacyProvider = mockk(relaxed = true)
        gppProvider = mockk(relaxed = true)

        subject = PrivacyService(tcfProvider, usPrivacyProvider, gppProvider)
    }

    @After
    fun tearDown() {
        unmockkObject(GeoInfoHolder)
    }

    @Test
    fun `shouldClearPersonalData - non-US user returns false`() = runTest {
        // Given - user is not in US
        every { GeoInfoHolder.isUSUser() } returns false

        // When
        val result = subject.shouldClearPersonalData()

        // Then - no personal data clearing required for non-US users
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldClearPersonalData - US user with COPPA returns true`() = runTest {
        // Given - US user with COPPA flag set
        every { GeoInfoHolder.isUSUser() } returns true
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = true)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - COPPA users always require personal data clearing
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldClearPersonalData - California user with GPP opt-out returns true`() = runTest {
        // Given - California user with CA GPP opt-out
        every { GeoInfoHolder.isUSUser() } returns true
        every { GeoInfoHolder.isCaliforniaUser() } returns true
        val gppConsent = GppConsent(saleOptOut = 1, sharingOptOut = null)
        every { gppProvider.decodeGpp(GppTarget.US_CA) } returns gppConsent
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - CA user with opt-out requires personal data clearing
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldClearPersonalData - California user without GPP opt-out returns false`() = runTest {
        // Given - California user with CA GPP no opt-out
        every { GeoInfoHolder.isUSUser() } returns true
        every { GeoInfoHolder.isCaliforniaUser() } returns true
        val gppConsent = GppConsent(saleOptOut = 2, sharingOptOut = 2)
        every { gppProvider.decodeGpp(GppTarget.US_CA) } returns gppConsent
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - CA user without opt-out does not require personal data clearing
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldClearPersonalData - non-California US user with GPP opt-out returns true`() = runTest {
        // Given - US non-CA user with National GPP opt-out
        every { GeoInfoHolder.isUSUser() } returns true
        every { GeoInfoHolder.isCaliforniaUser() } returns false
        val gppConsent = GppConsent(saleOptOut = 1, sharingOptOut = null)
        every { gppProvider.decodeGpp(GppTarget.US_NATIONAL) } returns gppConsent
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - US user with opt-out requires personal data clearing
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldClearPersonalData - non-California US user without GPP opt-out returns false`() = runTest {
        // Given - US non-CA user with National GPP no opt-out
        every { GeoInfoHolder.isUSUser() } returns true
        every { GeoInfoHolder.isCaliforniaUser() } returns false
        val gppConsent = GppConsent(saleOptOut = 2, sharingOptOut = 2)
        every { gppProvider.decodeGpp(GppTarget.US_NATIONAL) } returns gppConsent
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - US user without opt-out does not require personal data clearing
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldClearPersonalData - US user with no GPP data returns false`() = runTest {
        // Given - US user but no GPP data available
        every { GeoInfoHolder.isUSUser() } returns true
        every { GeoInfoHolder.isCaliforniaUser() } returns false
        every { gppProvider.decodeGpp(GppTarget.US_NATIONAL) } returns null
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.shouldClearPersonalData()

        // Then - no GPP data means no opt-out, no clearing required
        assertThat(result).isFalse()
    }

    @Test
    fun `isCoppaEnabled - returns true when isAgeRestrictedUser is true`() = runTest {
        // Given
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = true)

        // When
        val result = subject.isCoppaEnabled()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `isCoppaEnabled - returns false when isAgeRestrictedUser is false`() = runTest {
        // Given
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = false)

        // When
        val result = subject.isCoppaEnabled()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `isCoppaEnabled - returns false when isAgeRestrictedUser is null`() = runTest {
        // Given
        subject.cloudXPrivacy.value = CloudXPrivacy(isAgeRestrictedUser = null)

        // When
        val result = subject.isCoppaEnabled()

        // Then
        assertThat(result).isFalse()
    }
}
