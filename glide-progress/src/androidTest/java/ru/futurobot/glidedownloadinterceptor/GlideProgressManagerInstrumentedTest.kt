package ru.futurobot.glidedownloadinterceptor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [GlideProgressManager] running on an Android device.
 */
@RunWith(AndroidJUnit4::class)
class GlideProgressManagerInstrumentedTest {

    private lateinit var manager: GlideProgressManager

    @Before
    fun setUp() {
        manager = GlideProgressManager.getInstance()
        manager.clearListeners()
    }

    @After
    fun tearDown() {
        manager.clearListeners()
    }

    @Test
    fun singleton_returnsSameInstanceOnAndroid() {
        val instance1 = GlideProgressManager.getInstance()
        val instance2 = GlideProgressManager.getInstance()
        assertTrue(instance1 === instance2)
    }

    @Test
    fun addAndRemove_listenerWorksOnAndroid() {
        var callCount = 0
        val listener = ProgressListener { _, _, _ -> callCount++ }

        manager.addListener(listener)
        assertEquals(1, manager.listenerCount())

        manager.notifyProgress(100, 200, false)
        assertEquals(1, callCount)

        manager.removeListener(listener)
        assertEquals(0, manager.listenerCount())

        manager.notifyProgress(100, 200, false)
        assertEquals(1, callCount) // Should not have been called again
    }

    @Test
    fun clearListeners_worksOnAndroid() {
        manager.addListener(ProgressListener { _, _, _ -> })
        manager.addListener(ProgressListener { _, _, _ -> })
        assertEquals(2, manager.listenerCount())

        manager.clearListeners()
        assertEquals(0, manager.listenerCount())
    }
}
