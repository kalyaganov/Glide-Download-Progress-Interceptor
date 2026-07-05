package ru.futurobot.glidedownloadinterceptor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GlideProgressManagerTest {

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
    fun `singleton returns same instance`() {
        val instance1 = GlideProgressManager.getInstance()
        val instance2 = GlideProgressManager.getInstance()
        assertTrue(instance1 === instance2)
    }

    @Test
    fun `addListener registers a listener`() {
        manager.addListener(ProgressListener { _, _, _ -> })
        assertEquals(1, manager.listenerCount())
        assertTrue(manager.hasListeners())
    }

    @Test
    fun `removeListener unregisters a listener`() {
        val listener = ProgressListener { _, _, _ -> }
        manager.addListener(listener)
        assertEquals(1, manager.listenerCount())

        val removed = manager.removeListener(listener)
        assertTrue(removed)
        assertEquals(0, manager.listenerCount())
    }

    @Test
    fun `removeListener returns false for unregistered listener`() {
        val listener = ProgressListener { _, _, _ -> }
        val removed = manager.removeListener(listener)
        assertFalse(removed)
    }

    @Test
    fun `clearListeners removes all`() {
        manager.addListener(ProgressListener { _, _, _ -> })
        manager.addListener(ProgressListener { _, _, _ -> })
        manager.addListener(ProgressListener { _, _, _ -> })
        assertEquals(3, manager.listenerCount())

        manager.clearListeners()
        assertEquals(0, manager.listenerCount())
        assertFalse(manager.hasListeners())
    }

    @Test
    fun `duplicate listener is not added twice`() {
        val listener = ProgressListener { _, _, _ -> }
        manager.addListener(listener)
        manager.addListener(listener)
        assertEquals(1, manager.listenerCount())
    }

    @Test
    fun `notifyProgress dispatches to all listeners`() {
        val latch = CountDownLatch(3)
        manager.addListener(ProgressListener { _, _, _ -> latch.countDown() })
        manager.addListener(ProgressListener { _, _, _ -> latch.countDown() })
        manager.addListener(ProgressListener { _, _, _ -> latch.countDown() })

        manager.notifyProgress(100, 200, false)

        assertTrue("All listeners should have been notified", latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `notifyProgress with done=true dispatches correctly`() {
        var totalBytes = 0L
        var isDone = false
        val listener = ProgressListener { bytesRead, _, done ->
            totalBytes = bytesRead
            isDone = done
        }
        manager.addListener(listener)
        manager.notifyProgress(1024, 1024, true)

        assertEquals(1024L, totalBytes)
        assertTrue(isDone)
    }

    @Test
    fun `notifyProgress tolerates listener exceptions`() {
        manager.addListener(ProgressListener { _, _, _ -> throw RuntimeException("Boom!") })

        var called = false
        manager.addListener(ProgressListener { _, _, _ -> called = true })

        // Should not throw
        manager.notifyProgress(50, 100, false)

        assertTrue("Second listener should still be called", called)
    }

    @Test
    fun `thread safety — concurrent add and notify`() {
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)

        val threads = (1..threadCount).map { i ->
            thread {
                val listener = ProgressListener { _, _, _ -> }
                manager.addListener(listener)
                startLatch.await()
                manager.notifyProgress(i.toLong(), threadCount.toLong(), false)
                manager.removeListener(listener)
                latch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        threads.forEach { it.join(1000) }
    }

    @Test
    fun `createProgressClient returns an OkHttpClient with interceptor`() {
        val client = GlideProgressManager.createProgressClient()
        // The client should have at least one network interceptor
        assertTrue(client.networkInterceptors.isNotEmpty())
    }
}
