package ru.kalyaganov.glidedownloadinterceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProgressResponseBodyTest {

    @Test
    fun `notifies progress on read`() {
        val content = "Hello, World!".repeat(100)
        val body = content.toResponseBody("text/plain".toMediaType())

        val progressEvents = mutableListOf<Triple<Long, Long, Boolean>>()
        val wrapped = ProgressResponseBody(body) { bytesRead, contentLength, done ->
            progressEvents.add(Triple(bytesRead, contentLength, done))
        }

        val result = wrapped.source().readUtf8()

        assertEquals(content, result)
        assertTrue("Should have at least one progress event", progressEvents.isNotEmpty())

        val lastEvent = progressEvents.last()
        assertTrue("Last event should indicate done", lastEvent.third)
        assertEquals(content.length.toLong(), lastEvent.first)
    }

    @Test
    fun `contentLength returns original value`() {
        val content = "test"
        val body = content.toResponseBody("text/plain".toMediaType())
        val wrapped = ProgressResponseBody(body) { _, _, _ -> }

        assertEquals(content.length.toLong(), wrapped.contentLength())
    }

    @Test
    fun `contentType returns original value`() {
        val contentType = "application/json".toMediaType()
        val body = "{}".toResponseBody(contentType)
        val wrapped = ProgressResponseBody(body) { _, _, _ -> }

        assertNotNull(wrapped.contentType())
        assertTrue(wrapped.contentType()!!.toString().contains("json"))
    }

    @Test
    fun `handles zero-length body`() {
        val body = "".toResponseBody("text/plain".toMediaType())
        val progressEvents = mutableListOf<Triple<Long, Long, Boolean>>()
        val wrapped = ProgressResponseBody(body) { bytesRead, contentLength, done ->
            progressEvents.add(Triple(bytesRead, contentLength, done))
        }

        val result = wrapped.source().readUtf8()

        assertEquals("", result)
        assertTrue(progressEvents.isNotEmpty())
        assertTrue(progressEvents.last().third)
    }

    @Test
    fun `exception during read is propagated`() {
        val faultySource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                throw IOException("Simulated network failure")
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }

        val body = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = 100L
            override fun source(): okio.BufferedSource = faultySource.buffer()
        }

        val errors = mutableListOf<IOException>()
        val wrapped = ProgressResponseBody(body) { _, _, _ -> }

        try {
            wrapped.source().readUtf8()
            fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            errors.add(e)
        }

        assertEquals(1, errors.size)
        assertEquals("Simulated network failure", errors[0].message)
    }

    @Test
    fun `reports correct byte count for partial reads`() {
        val content = "x".repeat(10000)
        val body = content.toResponseBody("text/plain".toMediaType())

        val totalBytes = longArrayOf(0L)
        val doneFlag = booleanArrayOf(false)
        val wrapped = ProgressResponseBody(body) { bytesRead, _, done ->
            totalBytes[0] = bytesRead
            doneFlag[0] = done
        }

        val source = wrapped.source()
        val buffer = Buffer()
        while (source.read(buffer, 100) != -1L) {
            // continue reading
        }

        assertEquals(content.length.toLong(), totalBytes[0])
        assertTrue(doneFlag[0])
    }
}
