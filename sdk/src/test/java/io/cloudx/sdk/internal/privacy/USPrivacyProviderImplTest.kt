package io.cloudx.sdk.internal.privacy

import android.content.SharedPreferences
import io.cloudx.sdk.CXTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class USPrivacyProviderImplTest : CXTest() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var subject: USPrivacyProviderImpl

    @Before
    fun before() {
        sharedPrefs = mockk(relaxed = true)
        subject = USPrivacyProviderImpl(sharedPrefs)
    }

    @Test
    fun shouldReturnCorrectUsPrivacyStringWhenSet() = runTest {
        val usPrivacyString1 = "1YYY"
        val usPrivacyString2 = "1---"

        every { sharedPrefs.getString(IABUSPrivacy_String, null) } returns usPrivacyString1
        val result1 = subject.usPrivacyString()

        every { sharedPrefs.getString(IABUSPrivacy_String, null) } returns usPrivacyString2
        val result2 = subject.usPrivacyString()

        assert(result1 == usPrivacyString1 && result2 == usPrivacyString2)
    }

    @Test
    fun shouldReturnNullWhenSharedPrefsFieldNotSet() = runTest {
        every { sharedPrefs.getString(IABUSPrivacy_String, null) } returns null
        val result = subject.usPrivacyString()
        assert(result == null)
    }

    @Test
    fun shouldReturnNullWhenSharedPrefsFieldIsBlank() = runTest {
        every { sharedPrefs.getString(IABUSPrivacy_String, null) } returns "     "
        val result = subject.usPrivacyString()
        assert(result == null)
    }

    @Test
    fun shouldReturnNullWhenSharedPrefsFieldIsEmpty() = runTest {
        every { sharedPrefs.getString(IABUSPrivacy_String, null) } returns ""
        val result = subject.usPrivacyString()
        assert(result == null)
    }

    @Test
    fun shouldReturnNullWhenSharedPrefsFieldIsOfDifferentType() = runTest {
        every { sharedPrefs.getString(IABUSPrivacy_String, null) } throws ClassCastException("Not a string")
        val result = subject.usPrivacyString()
        assert(result == null)
    }
}
