package io.cloudx.sdk

import android.content.Context
import io.cloudx.sdk.internal.ApplicationContext
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Roboelectric set-up test base.
 */
@Suppress("MemberVisibilityCanBePrivate")
@RunWith(RobolectricTestRunner::class)
open class RoboMockkTest : MockkTest() {

    protected val appContext: Context =
        RuntimeEnvironment.getApplication().applicationContext

    @Before
    open fun before() {
        // Initiate app context first; otherwise crashes are inevitable during other components initialization.
        ApplicationContext(appContext)
    }
}