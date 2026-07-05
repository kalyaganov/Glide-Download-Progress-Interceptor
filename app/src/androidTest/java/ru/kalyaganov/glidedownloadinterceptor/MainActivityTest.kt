package ru.kalyaganov.glidedownloadinterceptor

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [MainActivity].
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun activity_launchesSuccessfully() {
        activityRule.scenario.onActivity { activity ->
            // Verify the activity is created and not null
            assert(activity != null)
        }
    }
}
