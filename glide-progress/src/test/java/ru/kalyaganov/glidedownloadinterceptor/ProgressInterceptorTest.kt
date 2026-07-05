package ru.kalyaganov.glidedownloadinterceptor

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgressInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var manager: GlideProgressManager

    @Before
    fun setUp() {
        server = MockWebServer()
        manager = GlideProgressManager.getInstance()
        manager.clearListeners()
    }

    @After
    fun tearDown() {
        server.shutdown()
        manager.clearListeners()
    }

    @Test
    fun `interceptor reports progress via manager`() {
        val content = "Hello, Progress!".repeat(50)
        server.enqueue(
            MockResponse()
                .setBody(content)
                .setResponseCode(200)
        )

        val client = GlideProgressManager.createProgressClient(OkHttpClient())

        val events = mutableListOf<Pair<Long, Long>>()
        val listener = ProgressListener { bytesRead, contentLength, _ ->
            events.add(bytesRead to contentLength)
        }
        manager.addListener(listener)

        val request = Request.Builder().url(server.url("/test")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertNotNull(response.body)
        val body = response.body!!.string()
        assertEquals(content, body)

        manager.removeListener(listener)

        assertTrue("Should have received progress events", events.isNotEmpty())
        val lastEvent = events.last()
        assertEquals(content.length.toLong(), lastEvent.first)
        assertEquals(content.length.toLong(), lastEvent.second)
    }

    @Test
    fun `no listeners registered does not crash`() {
        server.enqueue(MockResponse().setBody("ok"))

        val client = GlideProgressManager.createProgressClient(OkHttpClient())
        val request = Request.Builder().url(server.url("/no-listeners")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("ok", response.body?.string())
    }
}
