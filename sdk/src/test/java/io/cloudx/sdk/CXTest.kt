package io.cloudx.sdk

import io.cloudx.sdk.internal.CXLogger
import io.mockk.junit4.MockKRule
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

abstract class CXTest {
    @get:Rule
    val globalBefore = GlobalBeforeRule()

    @get:Rule
    val mockkRule = MockKRule(this)
}

class GlobalBeforeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                CXLogger.isEnabled = false
                try {
                    base.evaluate()
                } finally {
                    // optional after each test
                }
            }
        }
    }
}
