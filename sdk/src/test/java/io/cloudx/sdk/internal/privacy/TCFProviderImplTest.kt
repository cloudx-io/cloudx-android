package io.cloudx.sdk.internal.privacy

import android.content.SharedPreferences
import io.cloudx.sdk.CXTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TCFProviderImplTest : CXTest() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var subject: TCFProviderImpl

    @Before
    fun before() {
        sharedPrefs = mockk(relaxed = true)
        subject = TCFProviderImpl(sharedPrefs)
    }

    @Test
    fun tcStringShouldReturnCorrectTCStringWhenSet() = runTest {
        val tcString1 = "asdasda.asdasdasdasdas"
        val tcString2 = "kljlw;ljflsj.qdwwqdqwdq"

        every { sharedPrefs.getString(IABTCF_TCString, null) } returns tcString1
        val result1 = subject.tcString()

        every { sharedPrefs.getString(IABTCF_TCString, null) } returns tcString2
        val result2 = subject.tcString()

        assert(result1 == tcString1 && result2 == tcString2)
    }

    @Test
    fun tcStringShouldReturnNullWhenSharedPrefsFieldNotSet() = runTest {
        every { sharedPrefs.getString(IABTCF_TCString, null) } returns null
        val result = subject.tcString()
        assert(result == null)
    }

    @Test
    fun tcStringShouldReturnNullWhenSharedPrefsFieldIsBlank() = runTest {
        every { sharedPrefs.getString(IABTCF_TCString, null) } returns "     "
        val result = subject.tcString()
        assert(result == null)
    }

    @Test
    fun tcStringShouldReturnNullWhenSharedPrefsFieldIsEmpty() = runTest {
        every { sharedPrefs.getString(IABTCF_TCString, null) } returns ""
        val result = subject.tcString()
        assert(result == null)
    }

    @Test
    fun tcStringShouldReturnNullWhenSharedPrefsFieldIsOfDifferentType() = runTest {
        every { sharedPrefs.getString(IABTCF_TCString, null) } throws ClassCastException("Not a string")
        val result = subject.tcString()
        assert(result == null)
    }

    @Test
    fun gdprAppliesReturnNullWhenShardPrefsFieldNotSet() = runTest {
        every { sharedPrefs.contains(IABTCF_gdprApplies) } returns false
        val result = subject.gdprApplies()
        assert(result == null)
    }

    @Test
    fun gdprAppliesShouldReturnNullWhenSharedPrefsFieldIsNotZeroOrOne() = runTest {
        every { sharedPrefs.contains(IABTCF_gdprApplies) } returns true
        every { sharedPrefs.getInt(IABTCF_gdprApplies, 0) } returns 5
        val result = subject.gdprApplies()
        assert(result == null)
    }

    @Test
    fun gdprAppliesShouldReturnNullWhenSharedPrefsFieldIsOfDifferentType() = runTest {
        every { sharedPrefs.contains(IABTCF_gdprApplies) } returns true
        every { sharedPrefs.getInt(IABTCF_gdprApplies, 0) } throws ClassCastException("Not an int")
        val result = subject.gdprApplies()
        assert(result == null)
    }

    @Test
    fun gdprAppliesShouldReturnTrueWhenSharedPrefsFieldIsOne() = runTest {
        every { sharedPrefs.contains(IABTCF_gdprApplies) } returns true
        every { sharedPrefs.getInt(IABTCF_gdprApplies, 0) } returns 1
        val result = subject.gdprApplies()
        assert(result == true)
    }

    @Test
    fun gdprAppliesShouldReturnFalseWhenSharedPrefsFieldIsZero() = runTest {
        every { sharedPrefs.contains(IABTCF_gdprApplies) } returns true
        every { sharedPrefs.getInt(IABTCF_gdprApplies, 0) } returns 0
        val result = subject.gdprApplies()
        assert(result == false)
    }
}
