package ru.futurobot.glidedownloadinterceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.IOException
import okio.Source
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProgressResponseBodyTest {

    @Test
    fun `notifies progress on read`() {
        val content = "Hello, World!".repeat(100)
        val body = ResponseBody.create(
            "text/plain".toMediaType(),
            content
        )

        val progressEvents = mutableListOf<Triple<Long, Long, Boolean>>()
        val wrapped = ProgressResponseBody(body) { bytesRead, contentLength, done ->
            progressEvents.add(Triple(bytesRead, contentLength, done))
        }

        // Read the entire body
        val result = wrapped.source().readUtf8()

        assertEquals(content, result)
        assertTrue("Should have at least one progress event", progressEvents.isNotEmpty())

        // Last event should be done=true
        val lastEvent = progressEvents.last()
        assertTrue("Last event should indicate done", lastEvent.third)
        assertEquals(content.length.toLong(), lastEvent.first)
    }

    @Test
    fun `contentLength returns original value`() {
        val content = "test"
        val body = ResponseBody.create("text/plain".toMediaType(), content)
        val wrapped = ProgressResponseBody(body) { _, _, _ -> }

        assertEquals(content.length.toLong(), wrapped.contentLength())
    }

    @Test
    fun `contentType returns original value`() {
        val contentType = "application/json".toMediaType()
        val body = ResponseBody.create(contentType, "{}")
        val wrapped = ProgressResponseBody(body) { _, _, _ -> }

        assertEquals(contentType, wrapped.contentType())
    }

    @Test
    fun `handles zero-length body`() {
        val body = ResponseBody.create("text/plain".toMediaType(), "")
        val progressEvents = mutableListOf<Triple<Long, Long, Boolean>>()
        val wrapped = ProgressResponseBody(body) { bytesRead, contentLength, done ->
            progressEvents.add(Triple(bytesRead, contentLength, done))
        }

        val result = wrapped.source().readUtf8()

        assertEquals("", result)
        // Should still get the "done" notification
        assertTrue(progressEvents.isNotEmpty())
        assertTrue(progressEvents.last().third)
    }

    @Test
    fun `exception during read is propagated`() {
        // Create a body whose source throws on read
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
        // Create a body larger than the default buffer size to force multiple reads
        val content = "x".repeat(10000)
        val body = ResponseBody.create("text/plain".toMediaType(), content)

        val totalBytes = longArrayOf(0L)
        val doneFlag = booleanArrayOf(false)
        val wrapped = ProgressResponseBody(body) { bytesRead, _, done ->
            totalBytes[0] = bytesRead
            doneFlag[0] = done
        }

        val source = wrapped.source()
        val buffer = Buffer()
        // Read in small chunks
        while (source.read(buffer, 100) != -1L) {
            // continue reading
        }

        assertEquals(content.length.toLong(), totalBytes[0])
        assertTrue(doneFlag[0])
    }
}
